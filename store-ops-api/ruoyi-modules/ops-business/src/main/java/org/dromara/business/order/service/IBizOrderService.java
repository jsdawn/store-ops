package org.dromara.business.order.service;

import org.dromara.business.coupon.domain.vo.CouponOptionVo;
import org.dromara.business.order.domain.bo.BizOrderCreateBo;
import org.dromara.business.order.domain.bo.BizOrderPayBo;
import org.dromara.business.order.domain.bo.BizOrderQueryBo;
import org.dromara.business.order.domain.vo.BizOrderVo;
import org.dromara.business.order.domain.vo.OrderTodayVo;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单 服务接口
 *
 * <p>收银两段式（审查结论③/D10）：create 待收款 → pay 已完成；作废兜底；余额收款冲正独立动作。
 * member/coupon 域能力一律走对方 service 门面，禁止跨包注入 Mapper（守拆分演进纪律）。</p>
 *
 * @author store-ops
 */
public interface IBizOrderService {

    /**
     * 分页查询订单列表（数据权限过滤：店长本店及以下、租户管理员全部）
     *
     * @param bo        筛选条件（门店/状态/支付方式/订单号/手机号/时间区间）
     * @param pageQuery 分页参数
     * @return 订单分页列表（带 storeName）
     */
    TableDataInfo<BizOrderVo> selectPageOrderList(BizOrderQueryBo bo, PageQuery pageQuery);

    /**
     * 订单详情（含明细行、三段金额、券信息占位）
     *
     * @param orderId 订单ID
     * @return 订单详情
     */
    BizOrderVo queryById(Long orderId);

    /**
     * 今日流水汇总（营业额/单量仅算已完成；列表含待收款带状态，倒序上限 50；员工端 /mp/staff/** 共用）
     *
     * @param storeId 门店ID
     * @return 今日流水
     */
    OrderTodayVo queryToday(Long storeId);

    /**
     * 开单（→待收款）。行金额与总额服务端计算，pay_amount 恒 >0（杜绝 0 元单）
     *
     * @param bo 开单入参
     * @return 订单ID（连续收款用）
     */
    Long createByBo(BizOrderCreateBo bo);

    /**
     * 确认收款（→已完成）。核心事务：余额扣减/积分落账 + 状态条件 UPDATE 防并发收款（详设 5.2）
     *
     * @param orderId 订单ID
     * @param bo      支付方式
     * @return 结果
     */
    Boolean pay(Long orderId, BizOrderPayBo bo);

    /**
     * 作废（仅待收款、原因必填，仅店长权限在接口层控制；不产生任何余额/积分/券变动）
     *
     * @param orderId 订单ID
     * @param reason  作废原因
     * @return 结果
     */
    Boolean voidOrder(Long orderId, String reason);

    /**
     * 追加备注（仅已完成，错账备注用；服务端拼 [MM-dd HH:mm 操作人]，累计 255 上限）
     *
     * @param orderId 订单ID
     * @param remark  备注内容
     * @return 结果
     */
    Boolean appendRemark(Long orderId, String remark);

    /**
     * 余额收款错账冲正（仅已完成 + 余额收款 + 当日 + 未冲正过；加回余额、订单状态不变，详设 5.6 / D4）
     *
     * @param orderId 订单ID
     * @param reason  冲正原因
     * @return 结果
     */
    Boolean balanceReversal(Long orderId, String reason);

    /**
     * 收银选券候选（券域 P3 前恒空列表，见 IBizCouponService stub）
     *
     * @param memberId    会员ID
     * @param storeId     门店ID
     * @param totalAmount 订单总额（券前）
     * @return 券候选列表
     */
    List<CouponOptionVo> listCouponOptions(Long memberId, Long storeId, BigDecimal totalAmount);

}
