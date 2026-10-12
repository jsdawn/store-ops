package org.dromara.business.member.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.business.member.constant.OpsMemberConstants;
import org.dromara.business.member.domain.BizBalanceLog;
import org.dromara.business.member.domain.BizMember;
import org.dromara.business.member.domain.BizRechargeLog;
import org.dromara.business.member.domain.bo.BizRechargeLogBo;
import org.dromara.business.member.domain.vo.BizMemberVo;
import org.dromara.business.member.domain.vo.BizRechargeLogVo;
import org.dromara.business.member.mapper.BizBalanceLogMapper;
import org.dromara.business.member.mapper.BizMemberMapper;
import org.dromara.business.member.mapper.BizRechargeLogMapper;
import org.dromara.business.member.service.IBizRechargeLogService;
import org.dromara.business.store.service.IBizStoreService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 充值流水 服务层实现
 *
 * <p>冲正三重校验（详设 5.3）：仅当日（服务器时区自然日）、仅一次（status 乐观翻转兜底）、
 * 余额充足（条件 UPDATE 保证冲后余额 ≥ 0）；冲正动作在同一本地事务内扣余额 + 翻状态 + 记负向流水。</p>
 *
 * @author store-ops
 */
@RequiredArgsConstructor
@Service
public class BizRechargeLogServiceImpl implements IBizRechargeLogService {

    private final BizRechargeLogMapper baseMapper;
    private final BizMemberMapper memberMapper;
    private final BizBalanceLogMapper balanceLogMapper;
    private final IBizStoreService storeService;

    /**
     * 分页查询充值流水（数据权限过滤在 mapper 层）
     */
    @Override
    public TableDataInfo<BizRechargeLogVo> selectPageRechargeList(BizRechargeLogBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BizRechargeLog> wrapper = new LambdaQueryWrapper<>();
        // mobile/nickName 筛选：先解析为会员ID集合（无命中直接返回空页）
        List<Long> memberIds = resolveMemberIds(bo);
        if (memberIds != null && memberIds.isEmpty()) {
            return TableDataInfo.build(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()));
        }
        wrapper.in(memberIds != null, BizRechargeLog::getMemberId, memberIds)
            .eq(ObjectUtil.isNotNull(bo.getStoreId()), BizRechargeLog::getStoreId, bo.getStoreId())
            .eq(StringUtils.isNotBlank(bo.getPayType()), BizRechargeLog::getPayType, bo.getPayType())
            .eq(StringUtils.isNotBlank(bo.getStatus()), BizRechargeLog::getStatus, bo.getStatus())
            .ge(ObjectUtil.isNotNull(bo.getBeginTime()), BizRechargeLog::getCreateTime, bo.getBeginTime())
            .le(ObjectUtil.isNotNull(bo.getEndTime()), BizRechargeLog::getCreateTime, bo.getEndTime())
            .orderByDesc(BizRechargeLog::getCreateTime);

        Page<BizRechargeLogVo> page = baseMapper.selectPageRechargeList(pageQuery.build(), wrapper);
        fillRelateInfo(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * mobile/nickName 模糊 → 会员ID集合；均未填返回 null（不按会员过滤）
     */
    private List<Long> resolveMemberIds(BizRechargeLogBo bo) {
        if (StringUtils.isBlank(bo.getMobile()) && StringUtils.isBlank(bo.getNickName())) {
            return null;
        }
        LambdaQueryWrapper<BizMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(bo.getMobile()), BizMember::getMobile, bo.getMobile())
            .like(StringUtils.isNotBlank(bo.getNickName()), BizMember::getNickName, bo.getNickName())
            .select(BizMember::getMemberId);
        return memberMapper.selectList(wrapper).stream().map(BizMember::getMemberId).collect(Collectors.toList());
    }

    /**
     * 批量补齐 storeName、会员昵称/手机号、到账合计
     */
    private void fillRelateInfo(List<BizRechargeLogVo> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        List<Long> storeIds = list.stream().map(BizRechargeLogVo::getStoreId).distinct().collect(Collectors.toList());
        Map<Long, String> nameMap = storeService.selectStoreNameMap(storeIds);

        List<Long> memberIds = list.stream().map(BizRechargeLogVo::getMemberId).distinct().collect(Collectors.toList());
        Map<Long, BizMemberVo> memberMap = memberMapper.selectList(
                new LambdaQueryWrapper<BizMember>().in(BizMember::getMemberId, memberIds)).stream()
            .collect(Collectors.toMap(BizMember::getMemberId, m -> {
                BizMemberVo vo = new BizMemberVo();
                vo.setMemberId(m.getMemberId());
                vo.setNickName(m.getNickName());
                vo.setMobile(m.getMobile());
                return vo;
            }));

        list.forEach(vo -> {
            vo.setStoreName(nameMap.get(vo.getStoreId()));
            BizMemberVo member = memberMap.get(vo.getMemberId());
            if (member != null) {
                vo.setNickName(member.getNickName());
                vo.setMobile(member.getMobile());
            }
            vo.setTotalAmount(vo.getAmount().add(vo.getGiftAmount()));
        });
    }

    /**
     * 充值冲正（同一本地事务：扣余额 + 翻状态 + 负向余额流水，详设 5.3）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean reversal(Long rechargeId, String reason) {
        // 数据权限版单查：范围外流水视为不存在
        BizRechargeLogVo vo = baseMapper.selectRechargeVoById(rechargeId);
        if (vo == null) {
            throw new ServiceException("充值流水不存在或无权操作");
        }
        if (OpsMemberConstants.RECHARGE_STATUS_REVERSED.equals(vo.getStatus())) {
            throw new ServiceException("该笔充值已冲正，不可重复冲正");
        }
        Date beginOfDay = DateUtil.beginOfDay(new Date());
        if (vo.getCreateTime() == null || vo.getCreateTime().before(beginOfDay)) {
            throw new ServiceException("仅限冲正当日的充值流水");
        }
        BigDecimal total = vo.getAmount().add(vo.getGiftAmount());

        // 条件 UPDATE：余额不足影响行数为 0（E7 冲正时余额已被消费）
        int rows = memberMapper.deductBalance(vo.getMemberId(), total);
        if (rows == 0) {
            throw new ServiceException("冲正失败：会员当前余额不足（需扣除 " + total + " 元）");
        }
        // 状态乐观翻转：并发冲正只有一个成功
        int updated = baseMapper.update(null, new LambdaUpdateWrapper<BizRechargeLog>()
            .set(BizRechargeLog::getStatus, OpsMemberConstants.RECHARGE_STATUS_REVERSED)
            .eq(BizRechargeLog::getRechargeId, rechargeId)
            .eq(BizRechargeLog::getStatus, OpsMemberConstants.RECHARGE_STATUS_VALID));
        if (updated == 0) {
            throw new ServiceException("该笔充值已冲正，不可重复冲正");
        }

        BizMember member = memberMapper.selectById(vo.getMemberId());
        BigDecimal afterBalance = member.getBalance();
        BizBalanceLog log = new BizBalanceLog();
        log.setStoreId(vo.getStoreId());
        log.setDeptId(member.getDeptId());
        log.setMemberId(vo.getMemberId());
        log.setChangeAmount(total.negate());
        log.setAfterBalance(afterBalance);
        log.setBizType(OpsMemberConstants.BALANCE_TYPE_REVERSAL);
        log.setBizNo(vo.getRechargeNo());
        log.setRemark(reason);
        balanceLogMapper.insert(log);
        return true;
    }

}
