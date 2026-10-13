package org.dromara.auth.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 端登录结果视图对象（POST /auth/mp/login 出参，详设 4.5）
 *
 * <p>命中档案：needBindMobile=false + token + member；未命中（首次登录/未建档）：
 * needBindMobile=true + openId，前端引导「完善会员信息」后调 /mp/member/bind-mobile 完成登录。</p>
 *
 * @author store-ops
 */
@Data
public class MpLoginVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否需确认手机号（未命中档案）
     */
    private Boolean needBindMobile;

    /**
     * 微信open_id（needBindMobile=true 时下发，绑定建档用）
     */
    private String openId;

    /**
     * 门店ID（回显）
     */
    private Long storeId;

    /**
     * 门店名称（登录页展示）
     */
    private String storeName;

    /**
     * mp 端访问 token（needBindMobile=false 时下发，header Mp-Token 携带）
     */
    private String token;

    /**
     * 有效期（秒）
     */
    private Long expiresIn;

    /**
     * 会员信息（needBindMobile=false 时下发）
     */
    private MpMemberVo member;

}
