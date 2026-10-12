package org.dromara.ops.api.constant;

/**
 * 门店经营 mp 端（顾客微信端）鉴权常量
 *
 * <p>mp 端顾客（biz_member）不进 Sa-Token pc 会话与 sys_client 体系：
 * ruoyi-auth 在 /auth/mp/login 签发<b>不透明 token</b>，写入 Redis（key = {@link #MP_TOKEN_KEY} + token，
 * value = memberId，雪花全局唯一），有效期 {@link #MP_TOKEN_TTL_DAYS} 天；
 * ops-business mp 接口按 header 取 token、按 key 读 Redis 校验并取回 memberId。</p>
 *
 * @author store-ops
 */
public class OpsMpConstants {

    /**
     * mp 端不透明 token 的 Redis key 前缀（完整 key = 前缀 + token，value = memberId）
     */
    public static final String MP_TOKEN_KEY = "ops:mp:token:";

    /**
     * mp 端 token 有效期（天）
     */
    public static final Long MP_TOKEN_TTL_DAYS = 30L;

    /**
     * mp 端请求携带 token 的 header 名
     */
    public static final String MP_TOKEN_HEADER = "Mp-Token";

}
