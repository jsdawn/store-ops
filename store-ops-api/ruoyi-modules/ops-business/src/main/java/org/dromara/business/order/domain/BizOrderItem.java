package org.dromara.business.order.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细对象 biz_order_item（逐笔录入，订单完成后不可变；改明细=作废重开）
 *
 * @author store-ops
 */
@Data
@TableName("biz_order_item")
public class BizOrderItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 明细ID（雪花）
     */
    @TableId(value = "item_id")
    private Long itemId;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 项目名称（选填，仅展示备注）
     */
    private String itemName;

    /**
     * 单价（元，>0）
     */
    private BigDecimal unitPrice;

    /**
     * 数量（1~999）
     */
    private Integer quantity;

    /**
     * 行金额（=单价×数量，服务端计算）
     */
    private BigDecimal lineAmount;

    /**
     * 创建时间
     */
    private java.util.Date createTime;

}
