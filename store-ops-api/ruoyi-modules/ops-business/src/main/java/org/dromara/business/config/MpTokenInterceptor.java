package org.dromara.business.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.core.constant.HttpStatus;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.ServletUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.ops.api.constant.OpsMpConstants;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * mp 端顾客 token 拦截器（网关白名单放行 /mp/** 后的自定义鉴权层，架构 8.3）
 *
 * <p>不透明 token：header {@link OpsMpConstants#MP_TOKEN_HEADER} → Redis 换 memberId；
 * 校验通过写入 request attribute 供控制器取用。放行路径（登录闭环前置接口）由注册处排除。</p>
 *
 * @author store-ops
 */
@Component
public class MpTokenInterceptor implements HandlerInterceptor {

    /**
     * request attribute：当前登录会员ID（雪花）
     */
    public static final String MP_MEMBER_ID_ATTR = "mp.memberId";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String token = request.getHeader(OpsMpConstants.MP_TOKEN_HEADER);
        Long memberId = null;
        if (token != null && !token.isBlank()) {
            memberId = RedisUtils.getCacheObject(OpsMpConstants.MP_TOKEN_KEY + token);
        }
        if (memberId == null) {
            ServletUtils.renderString(response, JsonUtils.toJsonString(R.fail(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录")));
            return false;
        }
        request.setAttribute(MP_MEMBER_ID_ATTR, memberId);
        return true;
    }

}
