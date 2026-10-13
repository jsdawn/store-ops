package org.dromara.business.member.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 端登录结果视图对象（确认手机号后重颁 token，详设 4.5）
 *
 * @author store-ops
 */
@Data
public class MpLoginVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * mp 端访问 token（header Mp-Token 携带）
     */
    private String token;

    /**
     * 有效期（秒）
     */
    private Long expiresIn;

    /**
     * 会员信息
     */
    private MpMemberVo member;

}
