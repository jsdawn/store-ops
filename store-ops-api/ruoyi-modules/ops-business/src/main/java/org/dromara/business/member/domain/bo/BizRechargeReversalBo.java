package org.dromara.business.member.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 充值冲正业务对象（仅管理台，冲正原因必填）
 *
 * @author store-ops
 */
@Data
public class BizRechargeReversalBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 冲正原因（必填，记入负向余额流水 remark）
     */
    @NotBlank(message = "冲正原因不能为空")
    @Size(min = 1, max = 100, message = "冲正原因长度不能超过{max}个字符")
    private String reason;

}
