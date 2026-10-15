package org.dromara.business.order.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细行入参（逐笔录入）
 *
 * @author store-ops
 */
@Data
public class BizOrderItemBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目名称（选填，仅展示备注）
     */
    @Size(min = 0, max = 50, message = "项目名称长度不能超过{max}个字符")
    private String itemName;

    /**
     * 单价（元，>0）
     */
    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0.01", message = "单价必须大于0")
    private BigDecimal unitPrice;

    /**
     * 数量（1~999）
     */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量最小为1")
    @Max(value = 999, message = "数量最大为999")
    private Integer quantity;

}
