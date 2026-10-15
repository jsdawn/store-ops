package org.dromara.business.order.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.business.coupon.domain.vo.CouponOptionVo;
import org.dromara.business.order.domain.bo.BizOrderCreateBo;
import org.dromara.business.order.domain.bo.BizOrderPayBo;
import org.dromara.business.order.domain.bo.BizOrderQueryBo;
import org.dromara.business.order.domain.bo.BizOrderReasonBo;
import org.dromara.business.order.domain.bo.BizOrderRemarkBo;
import org.dromara.business.order.domain.vo.BizOrderVo;
import org.dromara.business.order.domain.vo.OrderTodayVo;
import org.dromara.business.order.service.IBizOrderService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单管理（管理台 pc 与员工端小程序工作台共用）
 *
 * <p>「共用」指<b>员工端</b>小程序工作台：店员登录 sys_user 员工账号（与 pc 同一套 Sa-Token 会话），
 * 请求走网关 /business/** → /order/**（详设 4.2）。开单/收款仅工作台使用（权限 3036/3037），
 * 作废/冲正仅管理台入口。顾客端消费记录在 controller/mp/ 包另立（详设 4.3，本期后置）。</p>
 *
 * @author store-ops
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/order")
public class BizOrderController extends BaseController {

    private final IBizOrderService orderService;

    /**
     * 订单分页列表（筛选：门店/状态/支付方式/订单号/手机号/时间区间；数据权限自动过滤）
     */
    @SaCheckPermission("ops:order:list")
    @GetMapping("/list")
    public TableDataInfo<BizOrderVo> list(BizOrderQueryBo bo, PageQuery pageQuery) {
        return orderService.selectPageOrderList(bo, pageQuery);
    }

    /**
     * 订单详情（含明细行、三段金额、券信息占位）
     *
     * @param orderId 订单ID
     */
    @SaCheckPermission("ops:order:query")
    @GetMapping("/{orderId}")
    public R<BizOrderVo> getInfo(@PathVariable Long orderId) {
        return R.ok(orderService.queryById(orderId));
    }

    /**
     * 工作台今日流水（营业额/单量仅算已完成；列表含待收款带状态）
     *
     * @param storeId 门店ID
     */
    @SaCheckPermission("ops:order:list")
    @GetMapping("/today")
    public R<OrderTodayVo> today(@RequestParam Long storeId) {
        return R.ok(orderService.queryToday(storeId));
    }

    /**
     * 开单（→待收款；返回订单ID供工作台连续收款；明细与总额服务端复算）
     */
    @SaCheckPermission("ops:order:add")
    @Log(title = "收银开单", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Long> add(@Validated @RequestBody BizOrderCreateBo bo) {
        return R.ok("开单成功", orderService.createByBo(bo));
    }

    /**
     * 确认收款（→已完成；余额收款同事务扣减，不足整单失败；幂等防重复提交）
     *
     * @param orderId 订单ID
     * @param bo      支付方式
     */
    @SaCheckPermission("ops:order:pay")
    @Log(title = "确认收款", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/{orderId}/pay")
    public R<Void> pay(@PathVariable Long orderId, @Validated @RequestBody BizOrderPayBo bo) {
        return toAjax(orderService.pay(orderId, bo));
    }

    /**
     * 作废（仅待收款、原因必填；权限仅店长，3033 不授予店员）
     *
     * @param orderId 订单ID
     * @param bo      作废原因
     */
    @SaCheckPermission("ops:order:void")
    @Log(title = "订单作废", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/{orderId}/void")
    public R<Void> voidOrder(@PathVariable Long orderId, @Validated @RequestBody BizOrderReasonBo bo) {
        return toAjax(orderService.voidOrder(orderId, bo.getReason()));
    }

    /**
     * 追加备注（仅已完成，错账备注用）
     *
     * @param orderId 订单ID
     * @param bo      备注内容
     */
    @SaCheckPermission("ops:order:remark")
    @Log(title = "订单备注", businessType = BusinessType.UPDATE)
    @PutMapping("/{orderId}/remark")
    public R<Void> remark(@PathVariable Long orderId, @Validated @RequestBody BizOrderRemarkBo bo) {
        return toAjax(orderService.appendRemark(orderId, bo.getRemark()));
    }

    /**
     * 余额收款错账冲正（仅已完成 + 余额收款 + 当日 + 未冲正过；仅管理台入口）
     *
     * @param orderId 订单ID
     * @param bo      冲正原因
     */
    @SaCheckPermission("ops:order:reversal")
    @Log(title = "余额收款冲正", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/{orderId}/balance-reversal")
    public R<Void> balanceReversal(@PathVariable Long orderId, @Validated @RequestBody BizOrderReasonBo bo) {
        return toAjax(orderService.balanceReversal(orderId, bo.getReason()));
    }

    /**
     * 收银选券候选（券域 P3 前恒空列表，前端显示「优惠券功能上线后开放」）
     *
     * @param memberId    会员ID（散客返回空）
     * @param storeId     门店ID
     * @param totalAmount 订单总额（券前）
     */
    @SaCheckPermission("ops:order:pay")
    @GetMapping("/coupon-options")
    public R<List<CouponOptionVo>> couponOptions(@RequestParam(required = false) Long memberId,
                                                 @RequestParam Long storeId,
                                                 @RequestParam BigDecimal totalAmount) {
        return R.ok(orderService.listCouponOptions(memberId, storeId, totalAmount));
    }

}
