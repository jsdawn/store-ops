package org.dromara.business.member.controller.mp;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.business.member.domain.bo.MpBindMobileBo;
import org.dromara.business.member.domain.bo.MpSwitchStoreBo;
import org.dromara.business.member.domain.vo.MpLoginVo;
import org.dromara.business.member.domain.vo.MpMemberVo;
import org.dromara.business.member.domain.vo.MpStoreBriefVo;
import org.dromara.business.member.service.IMpMemberService;
import org.dromara.business.config.MpTokenInterceptor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * mp 端会员接口（顾客微信端，网关不剥前缀，详设 4.3）
 *
 * <p>鉴权：MpTokenInterceptor（登录闭环前置接口 bind-mobile 除外）；
 * 归属校验在 service 内显式比对 storeId / tenant_id，禁走框架数据权限（PRD 6.6）。</p>
 *
 * @author store-ops
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/member")
public class MpMemberController extends BaseController {

    private final IMpMemberService mpMemberService;

    /**
     * 确认手机号：按（当前店, 手机号）匹配 → 已有档案补绑/覆盖 open_id；无档案新建；返回 mp token（登录闭环）
     *
     * @param bo 绑定入参
     * @return token + 会员信息
     */
    @PostMapping("/bind-mobile")
    public R<MpLoginVo> bindMobile(@Validated @RequestBody MpBindMobileBo bo) {
        return R.ok(mpMemberService.bindMobile(bo));
    }

    /**
     * 当前店资产：卡号、昵称、余额、积分、门店名
     *
     * @param storeId 当前店ID（显式携带并做归属比对，详设 4.3）
     * @return 会员信息
     */
    @GetMapping("/me")
    public R<MpMemberVo> me(@NotNull(message = "门店ID不能为空") @RequestParam Long storeId,
                            @RequestAttribute(MpTokenInterceptor.MP_MEMBER_ID_ATTR) Long memberId) {
        return R.ok(mpMemberService.queryMember(memberId, storeId));
    }

    /**
     * 我的门店列表：open_id/手机号跨租户查已注册（营业中）门店，带资产摘要（切换门店弹层）
     *
     * @param memberId token 换出
     * @return 门店摘要列表（当前店置顶并标记 current）
     */
    @GetMapping("/stores")
    public R<List<MpStoreBriefVo>> stores(@RequestAttribute(MpTokenInterceptor.MP_MEMBER_ID_ATTR) Long memberId) {
        return R.ok(mpMemberService.listStores(memberId));
    }

    /**
     * 切换门店：按目标店档案重签 mp token（token 绑定档案ID，切店必须换签）
     *
     * @param bo       目标门店ID
     * @param memberId token 换出
     * @return 新店 token + 会员信息
     */
    @PostMapping("/switch-store")
    public R<MpLoginVo> switchStore(@Validated @RequestBody MpSwitchStoreBo bo,
                                    @RequestAttribute(MpTokenInterceptor.MP_MEMBER_ID_ATTR) Long memberId) {
        return R.ok(mpMemberService.switchStore(memberId, bo.getStoreId()));
    }

}
