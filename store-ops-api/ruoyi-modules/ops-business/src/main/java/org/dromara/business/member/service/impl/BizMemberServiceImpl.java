package org.dromara.business.member.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.business.member.constant.OpsMemberConstants;
import org.dromara.business.member.domain.BizBalanceLog;
import org.dromara.business.member.domain.BizMember;
import org.dromara.business.member.domain.BizPointLog;
import org.dromara.business.member.domain.BizRechargeLog;
import org.dromara.business.member.domain.bo.BizMemberBo;
import org.dromara.business.member.domain.bo.BizRechargeBo;
import org.dromara.business.member.domain.vo.BizMemberVo;
import org.dromara.business.member.mapper.BizBalanceLogMapper;
import org.dromara.business.member.mapper.BizMemberMapper;
import org.dromara.business.member.mapper.BizPointLogMapper;
import org.dromara.business.member.mapper.BizRechargeLogMapper;
import org.dromara.business.member.service.IBizMemberService;
import org.dromara.business.store.domain.vo.BizStoreVo;
import org.dromara.business.store.service.IBizStoreService;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会员 服务层实现
 *
 * <p>余额/积分变动均为条件 UPDATE 原子语句（详设 D4），充值/结算在同一本地事务内落账 +
 * 记流水（余额+流水模式，架构 4.2）；会员卡号 = 门店序号(4位) + 店内序列(4位)（详设 T1/T4）。</p>
 *
 * @author store-ops
 */
@RequiredArgsConstructor
@Service
public class BizMemberServiceImpl implements IBizMemberService {

    private final BizMemberMapper baseMapper;
    private final BizBalanceLogMapper balanceLogMapper;
    private final BizPointLogMapper pointLogMapper;
    private final BizRechargeLogMapper rechargeLogMapper;
    private final IBizStoreService storeService;

    /**
     * 分页查询会员列表（数据权限过滤在 mapper 层）
     */
    @Override
    public TableDataInfo<BizMemberVo> selectPageMemberList(BizMemberBo bo, PageQuery pageQuery) {
        Page<BizMemberVo> page = baseMapper.selectPageMemberList(pageQuery.build(), buildQueryWrapper(bo));
        fillStoreInfo(page.getRecords());
        return TableDataInfo.build(page);
    }

    private LambdaQueryWrapper<BizMember> buildQueryWrapper(BizMemberBo bo) {
        LambdaQueryWrapper<BizMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ObjectUtil.isNotNull(bo.getStoreId()), BizMember::getStoreId, bo.getStoreId())
            .like(StringUtils.isNotBlank(bo.getMobile()), BizMember::getMobile, bo.getMobile())
            .like(StringUtils.isNotBlank(bo.getMemberNo()), BizMember::getMemberNo, bo.getMemberNo())
            .like(StringUtils.isNotBlank(bo.getNickName()), BizMember::getNickName, bo.getNickName())
            .orderByDesc(BizMember::getCreateTime);
        return wrapper;
    }

    /**
     * 按 ID 查会员详情
     */
    @Override
    public BizMemberVo queryById(Long memberId) {
        BizMemberVo vo = baseMapper.selectVoById(memberId);
        if (vo != null) {
            fillStoreInfo(List.of(vo));
        }
        return vo;
    }

    /**
     * 线下登记建档（open_id 留空，顾客日后微信登录按详设 5.1 流程自动补绑合并）
     */
    @Override
    public Boolean insertByBo(BizMemberBo bo) {
        BizStoreVo store = checkStoreEnabled(bo.getStoreId());
        checkMobileUnique(bo.getStoreId(), bo.getMobile(), null);
        BizMember member = MapstructUtils.convert(bo, BizMember.class);
        member.setDeptId(store.getDeptId());
        member.setMemberNo(generateMemberNo(bo.getStoreId()));
        try {
            return baseMapper.insert(member) > 0;
        } catch (DuplicateKeyException e) {
            // 并发兜底：手机号冲突转友好提示；卡号撞号（seq 漂移）重试一次
            String message = StringUtils.defaultString(e.getMessage());
            if (message.contains("uk_tenant_store_mobile")) {
                throw new ServiceException("该手机号已在本店建档，请勿重复登记");
            }
            member.setMemberNo(generateMemberNo(bo.getStoreId()));
            try {
                return baseMapper.insert(member) > 0;
            } catch (DuplicateKeyException ex) {
                throw new ServiceException("会员建档失败，请重试");
            }
        }
    }

    /**
     * 收银/充值前选会员（前 20 条精简项，数据权限过滤在 mapper 层）
     */
    @Override
    public List<BizMemberVo> selectSearchList(BizMemberBo bo) {
        LambdaQueryWrapper<BizMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ObjectUtil.isNotNull(bo.getStoreId()), BizMember::getStoreId, bo.getStoreId());
        if (StringUtils.isNotBlank(bo.getKeyword())) {
            String keyword = bo.getKeyword().trim();
            wrapper.and(w -> w.like(BizMember::getMobile, keyword)
                .or().like(BizMember::getMemberNo, keyword)
                .or().like(BizMember::getNickName, keyword));
        }
        wrapper.orderByDesc(BizMember::getCreateTime)
            .last("LIMIT " + OpsMemberConstants.SEARCH_LIMIT);
        List<BizMemberVo> list = baseMapper.selectMemberList(wrapper);
        fillStoreInfo(list);
        return list;
    }

    /**
     * 充值入账（同一本地事务：加余额 + 充值流水 + 余额流水快照，详设 5.2）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean recharge(BizRechargeBo bo) {
        if (bo.getGiftAmount().compareTo(bo.getAmount()) > 0) {
            throw new ServiceException("赠送金额不能大于充值本金");
        }
        // 数据权限版单查：范围外会员视为不存在（越权拦截）
        BizMemberVo member = baseMapper.selectMemberVoById(bo.getMemberId());
        if (member == null) {
            throw new ServiceException("会员不存在或无权操作");
        }
        checkStoreEnabled(member.getStoreId());
        BigDecimal total = bo.getAmount().add(bo.getGiftAmount());
        baseMapper.addBalance(member.getMemberId(), total);

        String rechargeNo = generateRechargeNo();
        BizRechargeLog log = new BizRechargeLog();
        log.setRechargeNo(rechargeNo);
        log.setStoreId(member.getStoreId());
        log.setDeptId(member.getDeptId());
        log.setMemberId(member.getMemberId());
        log.setAmount(bo.getAmount());
        log.setGiftAmount(bo.getGiftAmount());
        log.setPayType(bo.getPayType());
        log.setOperatorId(LoginHelper.getUserId());
        log.setOperatorName(LoginHelper.getUsername());
        log.setStatus(OpsMemberConstants.RECHARGE_STATUS_VALID);
        log.setRemark(bo.getRemark());
        rechargeLogMapper.insert(log);

        BigDecimal afterBalance = baseMapper.selectById(member.getMemberId()).getBalance();
        BizMember entity = baseMapper.selectById(member.getMemberId());
        balanceLogMapper.insert(buildBalanceLog(entity, total, afterBalance,
            OpsMemberConstants.BALANCE_TYPE_RECHARGE, rechargeNo, null));
        return true;
    }

    /**
     * 【域间门面】原子扣减余额 + 记余额消费流水（biz_no=订单号）
     */
    @Override
    public boolean deductBalance(Long memberId, BigDecimal amount, String orderNo) {
        BizMember member = baseMapper.selectById(memberId);
        if (member == null) {
            return false;
        }
        int rows = baseMapper.deductBalance(memberId, amount);
        if (rows == 0) {
            return false;
        }
        BizMember latest = baseMapper.selectById(memberId);
        balanceLogMapper.insert(buildBalanceLog(latest, amount.negate(), latest.getBalance(),
            OpsMemberConstants.BALANCE_TYPE_CONSUME, orderNo, null));
        return true;
    }

    /**
     * 【域间门面】订单完成结算：1:1 加积分 + 累计消费冗余（单条原子 UPDATE）+ 积分流水
     */
    @Override
    public void settleOrder(Long memberId, BigDecimal payAmount, String orderNo) {
        BizMember member = baseMapper.selectById(memberId);
        if (member == null) {
            throw new ServiceException("会员不存在");
        }
        int rows = baseMapper.settleOrder(memberId, payAmount, payAmount);
        if (rows == 0) {
            throw new ServiceException("会员积分结算失败");
        }
        BigDecimal afterPoint = baseMapper.selectById(memberId).getPointBalance();
        pointLogMapper.insert(buildPointLog(member, payAmount, afterPoint,
            OpsMemberConstants.POINT_TYPE_EARN, orderNo, null));
    }

    /**
     * 【域间门面】原子扣减积分 + 记积分兑换流水（biz_no=券实例ID）
     */
    @Override
    public boolean deductPoints(Long memberId, BigDecimal points, Long couponId) {
        BizMember member = baseMapper.selectById(memberId);
        if (member == null) {
            return false;
        }
        int rows = baseMapper.deductPoint(memberId, points);
        if (rows == 0) {
            return false;
        }
        BigDecimal afterPoint = baseMapper.selectById(memberId).getPointBalance();
        pointLogMapper.insert(buildPointLog(member, points.negate(), afterPoint,
            OpsMemberConstants.POINT_TYPE_EXCHANGE, String.valueOf(couponId), null));
        return true;
    }

    /**
     * 构建余额流水
     */
    private BizBalanceLog buildBalanceLog(BizMember member, BigDecimal changeAmount, BigDecimal afterBalance,
                                          String bizType, String bizNo, String remark) {
        BizBalanceLog log = new BizBalanceLog();
        log.setStoreId(member.getStoreId());
        log.setDeptId(member.getDeptId());
        log.setMemberId(member.getMemberId());
        log.setChangeAmount(changeAmount);
        log.setAfterBalance(afterBalance);
        log.setBizType(bizType);
        log.setBizNo(bizNo);
        log.setRemark(remark);
        return log;
    }

    /**
     * 构建积分流水
     */
    private BizPointLog buildPointLog(BizMember member, BigDecimal changePoint, BigDecimal afterPoint,
                                      String bizType, String bizNo, String remark) {
        BizPointLog log = new BizPointLog();
        log.setStoreId(member.getStoreId());
        log.setDeptId(member.getDeptId());
        log.setMemberId(member.getMemberId());
        log.setChangePoint(changePoint);
        log.setAfterPoint(afterPoint);
        log.setBizType(bizType);
        log.setBizNo(bizNo);
        log.setRemark(remark);
        return log;
    }

    /**
     * 校验门店存在且启用（详设 E9：停用门店不可建档/充值，历史查询不受影响）
     */
    private BizStoreVo checkStoreEnabled(Long storeId) {
        BizStoreVo store = storeService.queryStoreById(storeId);
        if (store == null) {
            throw new ServiceException("门店不存在或不属于当前租户");
        }
        if (!SystemConstants.NORMAL.equals(store.getStatus())) {
            throw new ServiceException("门店已停用，不可办理会员业务");
        }
        return store;
    }

    /**
     * 校验手机号门店内唯一（排除自身）
     */
    private void checkMobileUnique(Long storeId, String mobile, Long excludeMemberId) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<BizMember>()
            .eq(BizMember::getStoreId, storeId)
            .eq(BizMember::getMobile, mobile)
            .ne(ObjectUtil.isNotNull(excludeMemberId), BizMember::getMemberId, excludeMemberId));
        if (exist) {
            throw new ServiceException("该手机号已在本店建档，请勿重复登记");
        }
    }

    /**
     * 生成会员卡号：门店序号(4位) + 店内序列(4位)，Redis INCR 序列 + 唯一索引兜底（详设 5.1 / T1 / T4）
     */
    private String generateMemberNo(Long storeId) {
        Integer storeSeq = storeService.selectStoreSeq(storeId);
        if (storeSeq == null) {
            throw new ServiceException("门店不存在，无法生成会员卡号");
        }
        long seq = RedisUtils.incrAtomicValue(OpsMemberConstants.MEMBER_NO_SEQ_KEY + storeId);
        return String.format("%04d%04d", storeSeq, seq % 10000);
    }

    /**
     * 生成充值流水号：RC + yyMMdd + 雪花后6位（详设 D5）
     */
    private String generateRechargeNo() {
        String datePart = DateUtil.format(new Date(), "yyMMdd");
        long tail = IdUtil.getSnowflakeNextId() % 1000000;
        return OpsMemberConstants.RECHARGE_NO_PREFIX + datePart + String.format("%06d", tail);
    }

    /**
     * 批量补齐 storeName 与 wxBound（store 域门面，停用门店名称也返回）
     */
    private void fillStoreInfo(List<BizMemberVo> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        List<Long> storeIds = list.stream().map(BizMemberVo::getStoreId).distinct().collect(Collectors.toList());
        Map<Long, String> nameMap = storeService.selectStoreNameMap(storeIds);
        list.forEach(vo -> {
            vo.setStoreName(nameMap.get(vo.getStoreId()));
            vo.setWxBound(StringUtils.isNotBlank(vo.getOpenId()));
        });
    }

}
