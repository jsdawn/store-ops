package org.dromara.business.coupon.service;

import org.dromara.business.coupon.domain.vo.CouponOptionVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 优惠券 服务接口（券域门面，P3 落地）
 *
 * <p>order 域收银流程对本接口的全部调用点（详设 4.4）：
选券候选在开单页、核销在收款事务内；券域真实实现落地后替换实现类即可，order 域零改动。</p>
 *
 * @author store-ops
 */
public interface IBizCouponService {

    /**
     * 收银选券候选：该会员在本店、未使用未过期、按 totalAmount 判定可用性（含不可用原因，供置灰）
     *
     * @param memberId    会员ID
     * @param storeId     门店ID
     * @param totalAmount 订单总额（券前）
     * @return 券候选列表（本期 stub 恒空）
     */
    List<CouponOptionVo> listUsableForOrder(Long memberId, Long storeId, BigDecimal totalAmount);

    /**
     * 核销：条件 UPDATE 未使用 → 已使用，记核销时间与订单号；并发双核销返回 false
     *
     * @param couponId 券实例ID
     * @param orderId  订单ID
     * @return 是否核销成功
     */
    boolean writeOff(Long couponId, Long orderId);

}
