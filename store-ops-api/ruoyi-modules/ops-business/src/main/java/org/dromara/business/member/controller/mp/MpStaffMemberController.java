package org.dromara.business.member.controller.mp;

import lombok.RequiredArgsConstructor;
import org.dromara.business.config.MpTokenInterceptor;
import org.dromara.business.member.domain.bo.MpStaffRechargeBo;
import org.dromara.business.member.domain.vo.MpStaffMemberBriefVo;
import org.dromara.business.staff.service.IMpStaffService;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * mp 员工端会员业务接口（小程序工作台「会员充值」，网关不剥前缀，详设 4.6 / 5.2）
 *
 * <p>鉴权：MpTokenInterceptor 同层拦截；token 内 employeeId（管理用户ID）非空才可访问，
 * 服务层每次请求实时复检员工身份（停用/调岗后旧 token 立即摘除）。
 * 权限不细分（决策 D10）：命中员工身份即可用全部 /mp/staff/** 功能；
 * 管理台 RBAC 权限标识仅作用于管理台接口。仅复用 member 域 service 层内核，不共用 URL。</p>
 *
 * @author store-ops
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/staff/member")
public class MpStaffMemberController extends BaseController {

    private final IMpStaffService mpStaffService;

    /**
     * 本店会员搜索（充值选会员弹层）：keyword 手机号/卡号/昵称模糊，前 20 条
     *
     * @param memberId   会员ID（token 换出）
     * @param employeeId 员工身份ID（token 换出）
     * @param keyword    搜索关键字（可空）
     * @return 会员精简列表
     */
    @GetMapping("/search")
    public R<List<MpStaffMemberBriefVo>> search(@RequestAttribute(MpTokenInterceptor.MP_MEMBER_ID_ATTR) Long memberId,
                                                @RequestAttribute(value = MpTokenInterceptor.MP_EMPLOYEE_ID_ATTR, required = false) Long employeeId,
                                                @RequestParam(required = false) String keyword) {
        return R.ok(mpStaffService.searchMembers(memberId, employeeId, keyword));
    }

    /**
     * 签发充值提交凭证（Redis 一次有效，5 分钟，防弱网重复提交）
     *
     * @return 提交凭证
     */
    @GetMapping("/recharge/token")
    public R<String> rechargeToken(@RequestAttribute(MpTokenInterceptor.MP_MEMBER_ID_ATTR) Long memberId,
                                   @RequestAttribute(value = MpTokenInterceptor.MP_EMPLOYEE_ID_ATTR, required = false) Long employeeId) {
        // 注意不能写 R.ok(token)：R.ok(T data) 与 R.ok(String msg) 重载在实参为 String 时
        // 编译器选后者，token 会进 msg 而 data 为 null
        R<String> result = R.ok();
        result.setData(mpStaffService.issueRechargeToken(memberId, employeeId));
        return result;
    }

    /**
     * 充值入账（线下收款后录入）：目标会员须属当前门店，即时生效不可撤销（错账走管理台冲正）
     *
     * @param bo 充值入参（含一次性提交凭证）
     * @return 结果
     */
    @PostMapping("/recharge")
    public R<Void> recharge(@Validated @RequestBody MpStaffRechargeBo bo,
                            @RequestAttribute(MpTokenInterceptor.MP_MEMBER_ID_ATTR) Long memberId,
                            @RequestAttribute(value = MpTokenInterceptor.MP_EMPLOYEE_ID_ATTR, required = false) Long employeeId) {
        mpStaffService.recharge(bo, memberId, employeeId);
        return R.ok();
    }

}
