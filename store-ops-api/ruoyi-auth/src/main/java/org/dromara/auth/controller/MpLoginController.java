package org.dromara.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.auth.domain.vo.MpLoginVo;
import org.dromara.auth.domain.vo.MpPhoneVo;
import org.dromara.auth.form.MpLoginBody;
import org.dromara.auth.form.MpPhoneBody;
import org.dromara.auth.service.MpLoginService;
import org.dromara.common.core.domain.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * mp 端顾客登录（微信登录，架构 8.3 / 详设 4.5）
 *
 * <p>顾客 member ≠ sys_user：不进 Sa-Token pc 会话与 sys_client 体系；
 * 网关白名单含 /auth/mp/login，服务内不走框架鉴权注解。</p>
 *
 * @author store-ops
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp")
public class MpLoginController {

    private final MpLoginService mpLoginService;

    /**
     * mp 端登录：code2session 换 open_id → 按（租户, 门店, open_id）查档案
     *
     * @param body 登录入参（code + storeId）
     * @return 命中档案：token + member；未命中：needBindMobile + openId
     */
    @PostMapping("/login")
    public R<MpLoginVo> mpLogin(@Valid @RequestBody MpLoginBody body) {
        return R.ok(mpLoginService.login(body));
    }

    /**
     * 手机号快速验证 code 解码：open-type="getPhoneNumber" 回调 code → 真实手机号
     *
     * <p>只做解码不做绑定；拿手机号后的建档/补绑/签发 token 仍走 ops-business
     * /mp/member/bind-mobile（绑定逻辑单点收口）。code 5 分钟有效且单次使用，
     * 前端拿到解码结果后立即调 bind-mobile。</p>
     *
     * @param body 手机号 code
     * @return 手机号
     */
    @PostMapping("/phone")
    public R<MpPhoneVo> mpPhone(@Valid @RequestBody MpPhoneBody body) {
        MpPhoneVo vo = new MpPhoneVo();
        vo.setPhone(mpLoginService.resolvePhone(body.getCode()));
        return R.ok(vo);
    }

}
