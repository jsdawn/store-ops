package org.dromara.business.order.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 确认收款入参 biz_order（→已完成）
 *
 * @author store-ops
 */
@Data
public class BizOrderPayBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 支付方式（order_pay_type：1余额 2微信 3支付宝 4现金）
     */
    @NotBlank(message = "支付方式不能为空")
    @Pattern(regexp = "^[1-4]$", message = "支付方式不正确")
    private String payType;

}
