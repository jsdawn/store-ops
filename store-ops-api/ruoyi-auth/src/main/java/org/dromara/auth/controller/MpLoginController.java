package org.dromara.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.auth.domain.vo.MpLoginVo;
import org.dromara.auth.form.MpLoginBody;
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

}
