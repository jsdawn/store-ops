package org.dromara.business.order.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 工作台今日流水视图（GET /order/today）
 *
 * <p>口径（审查结论⑦）：营业额/单量仅算已完成；列表含待收款并带状态，倒序上限 50 条。</p>
 *
 * @author store-ops
 */
@Data
public class OrderTodayVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 今日营业额（当日已完成应付合计）
     */
    private BigDecimal totalAmount;

    /**
     * 今日已完成单量
     */
    private Integer orderCount;

    /**
     * 今日订单列表（已完成+待收款，倒序，上限 50 条）
     */
    private List<BizOrderVo> list;

}
