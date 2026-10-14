package org.dromara.auth.domain.vo;

import lombok.Data;

/**
 * mp 端手机号解码结果（getuserphonenumber.phone_info 精简）
 *
 * @author store-ops
 */
@Data
public class MpPhoneVo {

    /**
     * 不含区号的手机号（purePhoneNumber）
     */
    private String phone;

}
