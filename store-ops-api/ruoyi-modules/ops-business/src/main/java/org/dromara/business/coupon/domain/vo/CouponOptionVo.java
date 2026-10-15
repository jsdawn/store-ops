package org.dromara.business.coupon.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 收银选券候选项（券域 P3；本期 stub 返回空列表，结构先定，前端按空列表显示「上线后开放」）
 *
 * @author store-ops
 */
@Data
public class CouponOptionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 券实例ID
     */
    private Long couponId;

    /**
     * 券名称
     */
    private String couponName;

    /**
     * 优惠额（元）
     */
    private BigDecimal discountAmount;

    /**
     * 是否可用（不可用置灰展示）
     */
    private Boolean usable;

    /**
     * 不可用原因（不满足门槛/过期等）
     */
    private String unusableReason;

}
