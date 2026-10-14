package org.dromara.auth.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置（mp 顾客登录 code2session，详设 4.5）
 *
 * <p>appid/secret 存 Nacos（ruoyi-auth.yml）；开发期无真实小程序主体时开启 mock，
 * 跳过 code2session 直接注入固定 open_id（详设 T5 联调依赖）。</p>
 *
 * @author store-ops
 */
@Data
@Component
@ConfigurationProperties(prefix = "wx.mp")
public class WxMpProperties {

    /**
     * 是否开启 mock（true 时不调微信接口，固定返回 mockOpenId）
     */
    private boolean mockEnabled = true;

    /**
     * mock 固定 open_id
     */
    private String mockOpenId = "mock-open-id-001";

    /**
     * mock 固定手机号（手机号快速验证 code 解码 mock，配合真实主体上线前联调）
     */
    private String mockPhone = "13800138000";

    /**
     * 小程序 appid
     */
    private String appId;

    /**
     * 小程序 appsecret
     */
    private String secret;

}
