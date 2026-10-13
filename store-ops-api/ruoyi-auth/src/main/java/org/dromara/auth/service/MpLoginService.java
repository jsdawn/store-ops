package org.dromara.auth.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.auth.domain.vo.MpLoginVo;
import org.dromara.auth.domain.vo.MpMemberVo;
import org.dromara.auth.form.MpLoginBody;
import org.dromara.auth.properties.WxMpProperties;
import org.dromara.common.core.constant.HttpStatus;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.ops.api.RemoteMemberService;
import org.dromara.ops.api.RemoteStoreService;
import org.dromara.ops.api.constant.OpsMpConstants;
import org.dromara.ops.api.domain.vo.RemoteMemberVo;
import org.dromara.ops.api.domain.vo.RemoteStoreVo;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * mp 端顾客登录服务（微信登录，详设 4.5）
 *
 * <p>流程：code2session（或 mock）→ RemoteStoreService 解析门店/租户（校验营业状态）
 * → RemoteMemberService 按（租户, 门店, open_id）查档案 → 命中签发 token；未命中
 * 返回「需确认手机号」标记，前端引导 bind-mobile 完成建档/补绑（bind-mobile 侧重颁 token）。
 * token 为不透明串：Redis key = {@link OpsMpConstants#MP_TOKEN_KEY} + token，value = memberId（契约）。</p>
 *
 * @author store-ops
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MpLoginService {

    /**
     * 微信 code2session 接口地址
     */
    private static final String WX_JSCODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private final WxMpProperties wxMpProperties;

    @DubboReference
    private RemoteStoreService remoteStoreService;

    @DubboReference
    private RemoteMemberService remoteMemberService;

    /**
     * mp 端登录
     *
     * @param body 登录入参（code + storeId）
     * @return 登录结果（命中档案带 token；未命中带 needBindMobile + openId）
     */
    public MpLoginVo login(MpLoginBody body) {
        RemoteStoreVo store = remoteStoreService.selectByStoreId(body.getStoreId());
        if (store == null) {
            throw new ServiceException("门店不存在");
        }
        if (!"0".equals(store.getStatus())) {
            throw new ServiceException("门店暂停营业");
        }

        String openId = resolveOpenId(body.getCode());
        RemoteMemberVo member = remoteMemberService.selectByOpenId(store.getTenantId(), store.getStoreId(), openId);

        MpLoginVo vo = new MpLoginVo();
        vo.setNeedBindMobile(member == null);
        vo.setOpenId(openId);
        vo.setStoreId(store.getStoreId());
        vo.setStoreName(store.getStoreName());
        if (member != null) {
            String token = issueToken(member.getMemberId());
            vo.setToken(token);
            vo.setExpiresIn(OpsMpConstants.MP_TOKEN_TTL_DAYS * 24 * 3600);
            vo.setMember(BeanUtil.copyProperties(member, MpMemberVo.class));
        }
        return vo;
    }

    /**
     * 解析 open_id：mock 开启时直接注入固定值（T5 联调依赖），否则调 code2session
     */
    private String resolveOpenId(String jsCode) {
        if (wxMpProperties.isMockEnabled()) {
            log.info("mp 登录 mock 模式：注入固定 open_id={}", wxMpProperties.getMockOpenId());
            return wxMpProperties.getMockOpenId();
        }
        if (StringUtils.isBlank(wxMpProperties.getAppId()) || StringUtils.isBlank(wxMpProperties.getSecret())) {
            throw new ServiceException("小程序 appid/secret 未配置");
        }
        String url = WX_JSCODE2SESSION_URL
            + "?appid=" + wxMpProperties.getAppId()
            + "&secret=" + wxMpProperties.getSecret()
            + "&js_code=" + jsCode
            + "&grant_type=authorization_code";
        String resp = HttpUtil.get(url, 5000);
        JSONObject json = JSONUtil.parseObj(resp);
        if (json.getInt("errcode", 0) != 0 || StringUtils.isBlank(json.getStr("openid"))) {
            log.warn("code2session 失败：{}", resp);
            throw new ServiceException("微信登录失败：" + json.getStr("errmsg", "未知错误"));
        }
        return json.getStr("openid");
    }

    /**
     * 签发 mp 端 token（30 天，value = memberId，契约 OpsMpConstants）
     */
    private String issueToken(Long memberId) {
        String token = IdUtil.fastSimpleUUID();
        RedisUtils.setCacheObject(OpsMpConstants.MP_TOKEN_KEY + token, memberId,
            Duration.ofDays(OpsMpConstants.MP_TOKEN_TTL_DAYS));
        return token;
    }

}
