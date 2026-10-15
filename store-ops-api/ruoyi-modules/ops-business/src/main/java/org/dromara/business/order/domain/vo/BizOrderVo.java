package org.dromara.business.order.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.business.order.domain.BizOrder;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 订单视图对象 biz_order（金额三段由后端返回，前端不做减法）
 *
 * @author store-ops
 */
@Data
@AutoMapper(target = BizOrder.class)
public class BizOrderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单号（展示层脱敏为尾号）
     */
    private String orderNo;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 门店名称（store 域门面批量补齐）
     */
    private String storeName;

    /**
     * 会员ID（NULL=散客）
     */
    private Long memberId;

    /**
     * 会员昵称快照（散客展示「散客」由前端按 memberId 判定）
     */
    private String memberName;

    /**
     * 会员手机号快照（前端展示层脱敏）
     */
    private String memberMobile;

    /**
     * 订单总额（券前）
     */
    private BigDecimal totalAmount;

    /**
     * 优惠券实例ID（P3 前恒 NULL）
     */
    private Long couponId;

    /**
     * 券名（券域 P3 前恒空）
     */
    private String couponName;

    /**
     * 优惠券优惠额
     */
    private BigDecimal couponAmount;

    /**
     * 应付金额（=总额-优惠额）
     */
    private BigDecimal payAmount;

    /**
     * 支付方式（order_pay_type）
     */
    private String payType;

    /**
     * 状态（order_status）
     */
    private String status;

    /**
     * 本单积分
     */
    private BigDecimal earnedPoint;

    /**
     * 余额收款冲正标志（0否 1是）
     */
    private String balanceReversed;

    /**
     * 作废原因
     */
    private String voidReason;

    /**
     * 开单操作人姓名快照
     */
    private String operatorName;

    /**
     * 备注（追加式）
     */
    private String remark;

    /**
     * 开单时间
     */
    private Date createTime;

    /**
     * 明细行（仅详情接口填充）
     */
    private List<BizOrderItemVo> items;

}
