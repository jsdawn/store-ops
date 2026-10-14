package org.dromara.auth.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * mp 端手机号快速验证 code 入参（open-type="getPhoneNumber" 回调的 code）
 *
 * @author store-ops
 */
@Data
public class MpPhoneBody {

    /**
     * 微信手机号动态令牌（5 分钟有效、单次使用）
     */
    @NotBlank(message = "微信手机号 code 不能为空")
    private String code;

}
