package org.dromara.business.member.controller.mp;

import lombok.RequiredArgsConstructor;
import org.dromara.business.config.MpTokenInterceptor;
import org.dromara.business.staff.domain.vo.MpStaffHomeVo;
import org.dromara.business.staff.service.IMpStaffService;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * mp 员工端工作台接口（小程序员工端，网关不剥前缀，详设 4.6）
 *
 * <p>鉴权：MpTokenInterceptor 同层拦截；token 内 employeeId（管理用户ID）非空才可访问，
 * 服务层每次请求实时复检员工身份（停用/调岗后旧 token 立即摘除）。
 * 权限不细分（决策 D10）：命中员工身份即可用全部 /mp/staff/** 功能；
 * 管理台 RBAC 权限标识仅作用于管理台接口。</p>
 *
 * @author store-ops
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/staff")
public class MpStaffHomeController extends BaseController {

    private final IMpStaffService mpStaffService;

    /**
     * 工作台首页：门店名 + 员工名（设计稿 39:418 Hero 展示数据）
     *
     * @param memberId   会员ID（token 换出）
     * @param employeeId 员工身份ID（token 换出）
     * @return 首页数据
     */
    @GetMapping("/home")
    public R<MpStaffHomeVo> home(@RequestAttribute(MpTokenInterceptor.MP_MEMBER_ID_ATTR) Long memberId,
                                 @RequestAttribute(value = MpTokenInterceptor.MP_EMPLOYEE_ID_ATTR, required = false) Long employeeId) {
        return R.ok(mpStaffService.home(memberId, employeeId));
    }

}
