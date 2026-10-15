package org.dromara.business.order.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.business.order.domain.BizOrderItem;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细视图对象 biz_order_item
 *
 * @author store-ops
 */
@Data
@AutoMapper(target = BizOrderItem.class)
public class BizOrderItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 明细ID
     */
    private Long itemId;

    /**
     * 项目名称
     */
    private String itemName;

    /**
     * 单价（元）
     */
    private BigDecimal unitPrice;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 行金额（=单价×数量）
     */
    private BigDecimal lineAmount;

}
