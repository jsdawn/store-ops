package org.dromara.business.member.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 端确认手机号业务对象（首次登录建档/补绑 open_id，详设 4.3 #1 / 5.1）
 *
 * @author store-ops
 */
@Data
public class MpBindMobileBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 门店ID（扫码 scene）
     */
    @NotNull(message = "门店ID不能为空")
    private Long storeId;

    /**
     * 微信open_id（/auth/mp/login 未命中档案时下发）
     */
    @NotBlank(message = "open_id不能为空")
    private String openId;

    /**
     * 手机号（11 位，前端拦截 + 后端二次校验）
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String mobile;

    /**
     * 昵称（必填，设计稿「完善会员信息」页）
     */
    @NotBlank(message = "昵称不能为空")
    private String nickName;

    /**
     * 微信头像URL（可空）
     */
    private String avatar;

}
