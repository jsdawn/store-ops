package org.dromara.business.member.domain.bo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 充值入账业务对象（管理台线下登记与员工端 /mp/staff/** 录入共用入参结构）
 *
 * @author store-ops
 */
@Data
public class BizRechargeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会员ID
     */
    @NotNull(message = "会员不能为空")
    private Long memberId;

    /**
     * 充值金额（本金，>0 且 ≤ 99999.99，两位小数）
     */
    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额必须大于0")
    @DecimalMax(value = "99999.99", message = "充值金额不能超过99999.99")
    @Digits(integer = 5, fraction = 2, message = "充值金额最多两位小数")
    private BigDecimal amount;

    /**
     * 赠送金额（0 ≤ gift ≤ amount）
     */
    @NotNull(message = "赠送金额不能为空")
    @DecimalMin(value = "0", message = "赠送金额不能为负")
    @Digits(integer = 5, fraction = 2, message = "赠送金额最多两位小数")
    private BigDecimal giftAmount;

    /**
     * 充值方式（1微信 2支付宝 3现金，仅记录不触发支付）
     */
    @NotBlank(message = "充值方式不能为空")
    private String payType;

    /**
     * 备注
     */
    private String remark;

}
