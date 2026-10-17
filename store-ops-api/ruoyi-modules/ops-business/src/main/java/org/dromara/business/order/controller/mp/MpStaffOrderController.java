package org.dromara.business.order.controller.mp;

import lombok.RequiredArgsConstructor;
import org.dromara.business.config.MpTokenInterceptor;
import org.dromara.business.order.domain.bo.BizOrderPayBo;
import org.dromara.business.order.domain.bo.MpStaffOrderCreateBo;
import org.dromara.business.staff.service.IMpStaffService;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * mp 员工端收银接口（小程序工作台「收银开单」，网关不剥前缀，详设 4.2 / 4.6）
 *
 * <p>鉴权：MpTokenInterceptor 同层拦截；token 内 employeeId（管理用户ID）非空才可访问，
 * 服务层每次请求实时复检员工身份（停用/调岗后旧 token 立即摘除）。
 * 权限不细分（决策 D10）：命中员工身份即可用全部 /mp/staff/** 功能；
 * 管理台 RBAC 权限标识仅作用于管理台接口。仅复用 order 域 service 层内核，不共用 URL。</p>
 *
 * @author store-ops
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/staff/order")
public class MpStaffOrderController extends BaseController {

    private final IMpStaffService mpStaffService;

    /**
     * 签发开单提交凭证（Redis 一次有效，5 分钟，防弱网重复开单）
     *
     * @param memberId   会员ID（token 换出）
     * @param employeeId 员工身份ID（token 换出）
     * @return 提交凭证
     */
    @PostMapping("/token")
    public R<String> token(@RequestAttribute(MpTokenInterceptor.MP_MEMBER_ID_ATTR) Long memberId,
                           @RequestAttribute(value = MpTokenInterceptor.MP_EMPLOYEE_ID_ATTR, required = false) Long employeeId) {
        // 注意不能写 R.ok(token)：R.ok(T data) 与 R.ok(String msg) 重载在实参为 String 时
        // 编译器选后者，token 会进 msg 而 data 为 null（同 MpStaffMemberController）
        R<String> result = R.ok();
        result.setData(mpStaffService.issueOrderToken(memberId, employeeId));
        return result;
    }

    /**
     * 开单（→待收款）：门店由员工所属门店解析，行金额/总额/应收服务端计算，返回订单ID供连续收款
     *
     * @param bo 开单入参（items[]、memberId?、一次性提交凭证）
     * @return 订单ID
     */
    @PostMapping
    public R<Long> create(@Validated @RequestBody MpStaffOrderCreateBo bo,
                          @RequestAttribute(MpTokenInterceptor.MP_MEMBER_ID_ATTR) Long memberId,
                          @RequestAttribute(value = MpTokenInterceptor.MP_EMPLOYEE_ID_ATTR, required = false) Long employeeId) {
        return R.ok("开单成功", mpStaffService.createOrder(bo, memberId, employeeId));
    }

    /**
     * 确认收款（→已完成；余额收款同事务扣减，不足整单失败；状态条件 UPDATE 防并发重复收款）
     *
     * @param orderId 订单ID
     * @param bo      支付方式（1余额 2微信 3支付宝 4现金）
     */
    @PostMapping("/{orderId}/pay")
    public R<Void> pay(@PathVariable Long orderId, @Validated @RequestBody BizOrderPayBo bo,
                       @RequestAttribute(MpTokenInterceptor.MP_MEMBER_ID_ATTR) Long memberId,
                       @RequestAttribute(value = MpTokenInterceptor.MP_EMPLOYEE_ID_ATTR, required = false) Long employeeId) {
        return toAjax(mpStaffService.payOrder(orderId, bo.getPayType(), memberId, employeeId));
    }

}
