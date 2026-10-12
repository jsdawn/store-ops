package org.dromara.business.member.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.business.member.domain.bo.BizMemberBo;
import org.dromara.business.member.domain.bo.BizRechargeBo;
import org.dromara.business.member.domain.vo.BizMemberVo;
import org.dromara.business.member.service.IBizMemberService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会员管理（管理台 pc 与员工端小程序工作台共用）
 *
 * <p>「共用」指<b>员工端</b>小程序工作台：店员登录 sys_user 员工账号（与 pc 同一套 Sa-Token 会话），
 * 请求走网关 /business/** → /member/**，与管理台复用同一组接口（详设 4.2）。
 * <b>顾客端</b>（biz_member 微信用户）走 /mp/** 路由（网关不剥前缀），
 * 接口在 controller/mp/ 包另立（详设 4.3），与本类无关。</p>
 *
 * <p>本期不提供编辑/删除会员接口：手机号=身份不可改，录错只能弃用重建（详设 T3）。</p>
 *
 * @author store-ops
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/member")
public class BizMemberController extends BaseController {

    private final IBizMemberService memberService;

    /**
     * 会员分页列表（筛选：门店/手机号/卡号/昵称；数据权限自动过滤）
     */
    @SaCheckPermission("ops:member:list")
    @GetMapping("/list")
    public TableDataInfo<BizMemberVo> list(BizMemberBo bo, PageQuery pageQuery) {
        return memberService.selectPageMemberList(bo, pageQuery);
    }

    /**
     * 收银/充值前选会员（keyword 手机号/卡号/昵称模糊 + storeId，前 20 条；重复建档前端跳转数据源，E3）
     */
    @SaCheckPermission("ops:member:list")
    @GetMapping("/search")
    public R<List<BizMemberVo>> search(BizMemberBo bo) {
        return R.ok(memberService.selectSearchList(bo));
    }

    /**
     * 会员详情（档案 + 余额 + 积分 + 累计消费）
     *
     * @param memberId 会员ID
     */
    @SaCheckPermission("ops:member:query")
    @GetMapping("/{memberId}")
    public R<BizMemberVo> getInfo(@PathVariable Long memberId) {
        return R.ok(memberService.queryById(memberId));
    }

    /**
     * 线下登记建档（mobile 本店唯一；重复时前端经 search 接口取得档案跳转，不重复建档）
     */
    @SaCheckPermission("ops:member:add")
    @Log(title = "会员管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated(BizMemberBo.AddGroup.class) @RequestBody BizMemberBo bo) {
        return toAjax(memberService.insertByBo(bo));
    }

    /**
     * 充值入账（线下收款记账式，仅记录不触发支付；幂等防重复提交）
     */
    @SaCheckPermission("ops:member:recharge")
    @Log(title = "会员充值", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/recharge")
    public R<Void> recharge(@Validated @RequestBody BizRechargeBo bo) {
        return toAjax(memberService.recharge(bo));
    }

}
