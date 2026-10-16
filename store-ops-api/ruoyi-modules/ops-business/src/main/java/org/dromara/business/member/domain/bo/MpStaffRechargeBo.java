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
 * mp 员工端充值入账业务对象（小程序工作台「会员充值」，详设 5.2 / 4.6）
 *
 * <p>金额约束与管理台 BizRechargeBo 同口径；额外携带一次性提交凭证
 * {@code rechargeToken}（Redis 一次有效，防弱网重复提交）。</p>
 *
 * @author store-ops
 */
@Data
public class MpStaffRechargeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会员ID（必须属当前门店，服务层校验）
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
     * 赠送金额（0 ≤ gift ≤ amount，超本金后端拒绝）
     */
    @NotNull(message = "赠送金额不能为空")
    @DecimalMin(value = "0", message = "赠送金额不能为负")
    @Digits(integer = 5, fraction = 2, message = "赠送金额最多两位小数")
    private BigDecimal giftAmount;

    /**
     * 充值方式（1微信 2支付宝 3现金，仅线下收款标记不触发支付）
     */
    @NotBlank(message = "充值方式不能为空")
    private String payType;

    /**
     * 备注（选填）
     */
    private String remark;

    /**
     * 充值提交凭证（/mp/staff/member/recharge/token 签发，一次有效）
     */
    @NotBlank(message = "提交凭证已失效，请刷新后重试")
    private String rechargeToken;

}
