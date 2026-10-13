package org.dromara.business.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * mp 端（顾客微信端）鉴权装配
 *
 * <p>顾客 member ≠ sys_user，不走框架 Sa-Token 登录态；网关白名单放行 /mp/** 后，
 * 由本拦截器按 mp token（Redis 不透明 token）自定义鉴权（架构 8.3 / 详设 4.3）。
 * 排除路径 = 登录闭环前置接口（拿不到 token 就要调的接口）。</p>
 *
 * @author store-ops
 */
@Configuration
@RequiredArgsConstructor
public class MpAuthConfiguration implements WebMvcConfigurer {

    private final MpTokenInterceptor mpTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(mpTokenInterceptor)
            .addPathPatterns("/mp/**")
            .excludePathPatterns(
                // 确认手机号（首次登录建档/补绑，调用时还未持有 token）
                "/mp/member/bind-mobile",
                // 门店信息（登录页展示门店名，无需登录）
                "/mp/store/info"
            );
    }

}
