package org.dromara.business.order.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 订单作废/余额冲正原因入参（两者均为原因必填）
 *
 * @author store-ops
 */
@Data
public class BizOrderReasonBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 原因（作废/冲正必填）
     */
    @NotBlank(message = "原因不能为空")
    @Size(min = 0, max = 200, message = "原因长度不能超过{max}个字符")
    private String reason;

}
