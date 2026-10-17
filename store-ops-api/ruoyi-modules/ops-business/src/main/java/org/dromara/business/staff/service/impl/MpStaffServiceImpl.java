package org.dromara.business.staff.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.business.member.constant.OpsMemberConstants;
import org.dromara.business.member.domain.BizMember;
import org.dromara.business.member.domain.bo.BizRechargeBo;
import org.dromara.business.member.domain.bo.MpStaffRechargeBo;
import org.dromara.business.member.domain.vo.MpStaffMemberBriefVo;
import org.dromara.business.member.mapper.BizMemberMapper;
import org.dromara.business.member.service.IBizMemberService;
import org.dromara.business.order.constant.OpsOrderConstants;
import org.dromara.business.order.domain.bo.BizOrderCreateBo;
import org.dromara.business.order.domain.bo.BizOrderPayBo;
import org.dromara.business.order.domain.bo.MpStaffOrderCreateBo;
import org.dromara.business.order.domain.vo.BizOrderVo;
import org.dromara.business.order.service.IBizOrderService;
import org.dromara.business.staff.domain.vo.MpStaffHomeVo;
import org.dromara.business.staff.service.IMpStaffService;
import org.dromara.business.store.domain.BizStore;
import org.dromara.business.store.mapper.BizStoreMapper;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.system.api.RemoteUserService;
import org.dromara.system.api.model.LoginUser;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * mp 员工端服务实现（小程序工作台）
 *
 * <p>身份复检：以识别口径实时重跑（会员手机号 × 门店对应部门下的管理用户，决策 D9），
 * 结果必须与 token 内 employeeId 一致——管理员停用、调岗、改手机号后旧 token 立即失效。</p>
 *
 * @author store-ops
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MpStaffServiceImpl implements IMpStaffService {

    private final BizMemberMapper memberMapper;
    private final BizStoreMapper storeMapper;
    private final IBizMemberService memberService;
    private final IBizOrderService orderService;

    @DubboReference
    private RemoteUserService remoteUserService;

    @Override
    public MpStaffHomeVo home(Long memberId, Long employeeId) {
        StaffContext ctx = requireStaffContext(memberId, employeeId);
        MpStaffHomeVo vo = new MpStaffHomeVo();
        vo.setStoreId(ctx.store().getStoreId());
        vo.setStoreName(ctx.store().getStoreName());
        vo.setStaffName(ctx.staffName());
        return vo;
    }

    @Override
    public List<MpStaffMemberBriefVo> searchMembers(Long memberId, Long employeeId, String keyword) {
        StaffContext ctx = requireStaffContext(memberId, employeeId);
        List<BizMember> list = TenantHelper.dynamic(ctx.store().getTenantId(), () ->
            memberMapper.selectList(new LambdaQueryWrapper<BizMember>()
                .eq(BizMember::getStoreId, ctx.store().getStoreId())
                .and(StringUtils.isNotBlank(keyword), w -> {
                    String k = keyword.trim();
                    w.like(BizMember::getMobile, k)
                        .or().like(BizMember::getMemberNo, k)
                        .or().like(BizMember::getNickName, k);
                })
                .orderByDesc(BizMember::getCreateTime)
                .last("LIMIT " + OpsMemberConstants.SEARCH_LIMIT)));
        return list.stream().map(m -> {
            MpStaffMemberBriefVo vo = new MpStaffMemberBriefVo();
            vo.setMemberId(m.getMemberId());
            vo.setNickName(m.getNickName());
            vo.setMobile(m.getMobile());
            vo.setMemberNo(m.getMemberNo());
            vo.setBalance(m.getBalance());
            vo.setPointBalance(m.getPointBalance());
            return vo;
        }).toList();
    }

    @Override
    public String issueRechargeToken(Long memberId, Long employeeId) {
        StaffContext ctx = requireStaffContext(memberId, employeeId);
        String token = IdUtil.fastSimpleUUID();
        // value 绑定门店上下文，防跨店重放
        RedisUtils.setCacheObject(OpsMemberConstants.MP_RECHARGE_TOKEN_KEY + token,
            ctx.member().getMemberId() + "|" + ctx.store().getStoreId(),
            Duration.ofMinutes(OpsMemberConstants.MP_RECHARGE_TOKEN_TTL_MINUTES));
        return token;
    }

    @Override
    public void recharge(MpStaffRechargeBo bo, Long memberId, Long employeeId) {
        StaffContext ctx = requireStaffContext(memberId, employeeId);
        // 提交凭证一次有效：不存在 / 不匹配当前门店上下文 → 拒绝（防弱网重复提交与跨店重放）
        String tokenKey = OpsMemberConstants.MP_RECHARGE_TOKEN_KEY + bo.getRechargeToken();
        Object cached = RedisUtils.getCacheObject(tokenKey);
        String expected = ctx.member().getMemberId() + "|" + ctx.store().getStoreId();
        if (cached == null || !expected.equals(cached.toString())) {
            throw new ServiceException("提交凭证已失效，请刷新后重试");
        }
        RedisUtils.deleteObject(tokenKey);
        // 目标会员必须属当前门店（员工数据权限，详设 5.2 校验链第 1 步）
        BizMember target = TenantHelper.ignore(() -> memberMapper.selectById(bo.getMemberId()));
        if (target == null || !ctx.store().getStoreId().equals(target.getStoreId())) {
            throw new ServiceException("会员不属于当前门店");
        }
        // 入账 + 流水复用 member 域共用内核；操作人 = 员工身份（管理用户，决策 D11）
        BizRechargeBo rechargeBo = new BizRechargeBo();
        rechargeBo.setMemberId(bo.getMemberId());
        rechargeBo.setAmount(bo.getAmount());
        rechargeBo.setGiftAmount(bo.getGiftAmount());
        rechargeBo.setPayType(bo.getPayType());
        rechargeBo.setRemark(bo.getRemark());
        TenantHelper.dynamic(ctx.store().getTenantId(), () ->
            memberService.rechargeByStaff(rechargeBo, employeeId, ctx.staffName()));
    }

    @Override
    public String issueOrderToken(Long memberId, Long employeeId) {
        StaffContext ctx = requireStaffContext(memberId, employeeId);
        String token = IdUtil.fastSimpleUUID();
        // value 绑定门店上下文，防跨店重放（与充值凭证同构）
        RedisUtils.setCacheObject(OpsOrderConstants.MP_ORDER_TOKEN_KEY + token,
            ctx.member().getMemberId() + "|" + ctx.store().getStoreId(),
            Duration.ofMinutes(OpsOrderConstants.MP_ORDER_TOKEN_TTL_MINUTES));
        return token;
    }

    @Override
    public Long createOrder(MpStaffOrderCreateBo bo, Long memberId, Long employeeId) {
        StaffContext ctx = requireStaffContext(memberId, employeeId);
        // 提交凭证一次有效：不存在 / 不匹配当前门店上下文 → 拒绝（防弱网重复开单与跨店重放）
        String tokenKey = OpsOrderConstants.MP_ORDER_TOKEN_KEY + bo.getOrderToken();
        Object cached = RedisUtils.getCacheObject(tokenKey);
        String expected = ctx.member().getMemberId() + "|" + ctx.store().getStoreId();
        if (cached == null || !expected.equals(cached.toString())) {
            throw new ServiceException("提交凭证已失效，请刷新后重试");
        }
        RedisUtils.deleteObject(tokenKey);
        // 组装管理台开单入参：门店 = 员工所属门店，操作人 = 员工身份（决策 D11）；
        // 会员归属由共用内核内校验（selectMemberVoById 同款口径：属本店才受理）
        BizOrderCreateBo createBo = new BizOrderCreateBo();
        createBo.setStoreId(ctx.store().getStoreId());
        createBo.setMemberId(bo.getMemberId());
        createBo.setItems(bo.getItems());
        createBo.setOperatorId(employeeId);
        createBo.setOperatorName(ctx.staffName());
        return TenantHelper.dynamic(ctx.store().getTenantId(), () -> orderService.createByBo(createBo));
    }

    @Override
    public Boolean payOrder(Long orderId, String payType, Long memberId, Long employeeId) {
        StaffContext ctx = requireStaffContext(memberId, employeeId);
        // 决策 D10：mp 员工端不走 PC 数据权限——@DataPermission 注解方法（selectOrderVoById）内部
        // 依赖 Sa-Token 会话（LoginHelper），mp token 无会话会抛 NotLoginException；
        // 数据边界由下方 storeId 归属校验保证（员工只能操作本店订单）
        BizOrderVo order = TenantHelper.dynamic(ctx.store().getTenantId(),
            () -> DataPermissionHelper.ignore(() -> orderService.queryById(orderId)));
        if (order == null || !ctx.store().getStoreId().equals(order.getStoreId())) {
            throw new ServiceException("订单不存在或无权操作");
        }
        BizOrderPayBo payBo = new BizOrderPayBo();
        payBo.setPayType(payType);
        // 收款核心事务共用管理台内核：余额扣减/积分落账 + 状态条件 UPDATE 防并发收款（详设 5.2）；
        // pay 内部同样经 selectOrderVoById 复查订单，一并置于 ignore 范围
        return TenantHelper.dynamic(ctx.store().getTenantId(),
            () -> DataPermissionHelper.ignore(() -> orderService.pay(orderId, payBo)));
    }

    /**
     * 员工上下文（会员档案 + 门店 + 复检通过的员工姓名），工作台各接口共用前置
     */
    private record StaffContext(BizMember member, BizStore store, String staffName) {
    }

    /**
     * 解析并复检员工上下文：会员档案 → 门店（启用中）→ 员工身份实时重跑且与 token 声明一致
     */
    private StaffContext requireStaffContext(Long memberId, Long employeeId) {
        if (employeeId == null) {
            throw new ServiceException("当前账号不是门店员工");
        }
        BizMember member = TenantHelper.ignore(() -> memberMapper.selectById(memberId));
        if (member == null) {
            throw new ServiceException("会员不存在");
        }
        BizStore store = TenantHelper.ignore(() -> storeMapper.selectById(member.getStoreId()));
        if (store == null || !SystemConstants.NORMAL.equals(store.getStatus())) {
            throw new ServiceException("门店不存在或已停业");
        }
        String staffName = null;
        if (StringUtils.isNotBlank(member.getMobile()) && store.getDeptId() != null) {
            try {
                LoginUser user = remoteUserService.getUserInfoByPhonenumber(member.getMobile(), store.getTenantId());
                if (user != null && Objects.equals(store.getDeptId(), user.getDeptId())
                    && Objects.equals(employeeId, user.getUserId())) {
                    staffName = user.getNickname();
                }
            } catch (Exception e) {
                log.debug("mp 员工身份复检未通过：employeeId={}", employeeId);
            }
        }
        if (staffName == null) {
            throw new ServiceException("员工身份已失效，请重新登录后进入工作台");
        }
        return new StaffContext(member, store, staffName);
    }

}
