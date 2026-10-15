package org.dromara.business.order.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 今日流水汇总结果（mapper 聚合查询映射）
 *
 * @author store-ops
 */
@Data
public class OrderDailySummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 营业额合计（当日已完成应付合计）
     */
    private BigDecimal totalAmount;

    /**
     * 已完成单量
     */
    private Integer orderCount;

}
