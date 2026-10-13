package org.dromara.auth.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 端顾客登录入参（POST /auth/mp/login，详设 4.5）
 *
 * @author store-ops
 */
@Data
public class MpLoginBody implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * wx.login 临时凭证（mock 模式下后端不消费）
     */
    @NotBlank(message = "code不能为空")
    private String code;

    /**
     * 门店ID（扫码 scene）
     */
    @NotNull(message = "门店ID不能为空")
    private Long storeId;

}
