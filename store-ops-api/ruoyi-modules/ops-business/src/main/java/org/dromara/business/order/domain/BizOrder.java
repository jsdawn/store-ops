package org.dromara.business.order.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 订单主表对象 biz_order（快照性数据：会员昵称/手机号冗余快照；无 del_flag，作废代替删除）
 *
 * @author store-ops
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_order")
public class BizOrder extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 订单ID（雪花）
     */
    @TableId(value = "order_id")
    private Long orderId;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 冗余门店绑定部门ID（数据权限过滤）
     */
    private Long deptId;

    /**
     * 订单号（ORD+yyMMdd+雪花后6位，全局唯一）
     */
    private String orderNo;

    /**
     * 会员ID（NULL=散客）
     */
    private Long memberId;

    /**
     * 会员昵称快照（下单时刻）
     */
    private String memberName;

    /**
     * 会员手机号快照（下单时刻）
     */
    private String memberMobile;

    /**
     * 订单总额（元，明细合计，券前）
     */
    private BigDecimal totalAmount;

    /**
     * 优惠券实例ID（未用券为 NULL，券域 P3）
     */
    private Long couponId;

    /**
     * 优惠券优惠额（元）
     */
    private BigDecimal couponAmount;

    /**
     * 应付金额（元，=总额-优惠额，恒>0）
     */
    private BigDecimal payAmount;

    /**
     * 支付方式（order_pay_type：1余额 2微信 3支付宝 4现金，完成时填）
     */
    private String payType;

    /**
     * 状态（order_status：0待收款 1已完成 2已作废）
     */
    private String status;

    /**
     * 本单积分（=应付等额，散客为0，冗余展示）
     */
    private BigDecimal earnedPoint;

    /**
     * 余额收款冲正标志（0否 1是，防重复冲正）
     */
    private String balanceReversed;

    /**
     * 作废原因（作废时必填）
     */
    private String voidReason;

    /**
     * 开单操作人（sys_user）
     */
    private Long operatorId;

    /**
     * 开单操作人姓名快照
     */
    private String operatorName;

    /**
     * 备注（追加式：[MM-dd HH:mm 操作人] 内容）
     */
    private String remark;

}
