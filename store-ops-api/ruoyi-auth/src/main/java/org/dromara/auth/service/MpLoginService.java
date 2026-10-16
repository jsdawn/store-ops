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
import org.dromara.ops.api.domain.dto.MpTokenPayload;
import org.dromara.ops.api.domain.vo.RemoteMemberVo;
import org.dromara.ops.api.domain.vo.RemoteStoreVo;
import org.dromara.system.api.RemoteUserService;
import org.dromara.system.api.model.LoginUser;
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

    /**
     * 微信 stable_token 接口地址（免配额抖动，官方推荐替代 /cgi-bin/token）
     */
    private static final String WX_STABLE_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/stable_token";

    /**
     * 微信手机号快速验证解码接口地址（拼 access_token）
     */
    private static final String WX_PHONE_URL = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=";

    /**
     * 小程序接口调用凭据 Redis 缓存 key 前缀（按 appid 隔离）
     */
    private static final String MP_ACCESS_TOKEN_KEY = "global:mp:wx:access_token:";

    private final WxMpProperties wxMpProperties;

    @DubboReference
    private RemoteStoreService remoteStoreService;

    @DubboReference
    private RemoteMemberService remoteMemberService;

    @DubboReference
    private RemoteUserService remoteUserService;

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
            MpStaffIdentity staff = resolveStaff(store.getTenantId(), store.getDeptId(), member.getMobile());
            String token = issueToken(member.getMemberId(), staff == null ? null : staff.userId());
            vo.setToken(token);
            vo.setExpiresIn(OpsMpConstants.MP_TOKEN_TTL_DAYS * 24 * 3600);
            MpMemberVo memberVo = BeanUtil.copyProperties(member, MpMemberVo.class);
            if (staff != null) {
                memberVo.setEmployeeId(staff.userId());
                memberVo.setEmployeeName(staff.nickname());
            }
            vo.setMember(memberVo);
        }
        return vo;
    }

    /**
     * 员工身份记录（管理用户ID + 姓名，用于工作台展示与操作日志口径，详设 4.6 / D9）
     */
    record MpStaffIdentity(Long userId, String nickname) {
    }

    /**
     * 员工身份自动识别（决策 D9）：会员手机号命中「门店对应部门下、状态正常」的管理用户即员工。
     * 查无此人 / 停用 / 部门不符 / 远程调用异常一律视为非员工，不阻断顾客登录主链路
     */
    private MpStaffIdentity resolveStaff(String tenantId, Long deptId, String mobile) {
        if (StringUtils.isBlank(mobile) || deptId == null) {
            return null;
        }
        try {
            LoginUser user = remoteUserService.getUserInfoByPhonenumber(mobile, tenantId);
            if (user == null || !deptId.equals(user.getDeptId())) {
                return null;
            }
            return new MpStaffIdentity(user.getUserId(), user.getNickname());
        } catch (Exception e) {
            // 非员工（查无/停用抛 UserException）或 Dubbo 异常都按顾客处理
            log.debug("mp 员工身份未命中：tenantId={}, deptId={}, mobile={}", tenantId, deptId, mobile);
            return null;
        }
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
     * 解析手机号：mock 开启时返回固定手机号（T5 联调依赖），
     * 否则用手机号快速验证 code 调 getuserphonenumber 换真实手机号
     *
     * @param phoneCode open-type="getPhoneNumber" 回调下发的 code（5 分钟有效、单次使用）
     * @return 不含区号的手机号（purePhoneNumber）
     */
    public String resolvePhone(String phoneCode) {
        if (wxMpProperties.isMockEnabled()) {
            log.info("mp 手机号解码 mock 模式：返回固定手机号");
            return wxMpProperties.getMockPhone();
        }
        if (StringUtils.isBlank(wxMpProperties.getAppId()) || StringUtils.isBlank(wxMpProperties.getSecret())) {
            throw new ServiceException("小程序 appid/secret 未配置");
        }
        String resp = HttpUtil.post(WX_PHONE_URL + getAccessToken(),
            JSONUtil.createObj().set("code", phoneCode).toString(), 5000);
        JSONObject json = JSONUtil.parseObj(resp);
        if (json.getInt("errcode", 0) != 0) {
            log.warn("getuserphonenumber 失败：{}", resp);
            throw new ServiceException("微信手机号获取失败：" + json.getStr("errmsg", "未知错误"));
        }
        String phone = json.getJSONObject("phone_info").getStr("purePhoneNumber");
        if (StringUtils.isBlank(phone)) {
            throw new ServiceException("微信未返回手机号");
        }
        return phone;
    }

    /**
     * 获取小程序接口调用凭据 access_token：Redis 缓存优先（提前 200s 过期防边界失效），
     * 未命中走 stable_token（client_credential），写入缓存后返回
     */
    private String getAccessToken() {
        String key = MP_ACCESS_TOKEN_KEY + wxMpProperties.getAppId();
        String cached = RedisUtils.getCacheObject(key);
        if (StringUtils.isNotBlank(cached)) {
            return cached;
        }
        String resp = HttpUtil.post(WX_STABLE_TOKEN_URL, JSONUtil.createObj()
            .set("grant_type", "client_credential")
            .set("appid", wxMpProperties.getAppId())
            .set("secret", wxMpProperties.getSecret()).toString(), 5000);
        JSONObject json = JSONUtil.parseObj(resp);
        String accessToken = json.getStr("access_token");
        if (StringUtils.isBlank(accessToken)) {
            log.warn("stable_token 获取失败：{}", resp);
            throw new ServiceException("微信凭据获取失败：" + json.getStr("errmsg", "未知错误"));
        }
        Long expiresIn = json.getLong("expires_in", 7200L);
        RedisUtils.setCacheObject(key, accessToken, Duration.ofSeconds(Math.max(expiresIn - 200, 60)));
        return accessToken;
    }

    /**
     * 签发 mp 端 token（30 天，value = {@link MpTokenPayload}：memberId + 员工身份，契约 OpsMpConstants）
     */
    private String issueToken(Long memberId, Long employeeId) {
        String token = IdUtil.fastSimpleUUID();
        RedisUtils.setCacheObject(OpsMpConstants.MP_TOKEN_KEY + token,
            new MpTokenPayload(memberId, employeeId),
            Duration.ofDays(OpsMpConstants.MP_TOKEN_TTL_DAYS));
        return token;
    }

}
