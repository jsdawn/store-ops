package org.dromara.business.staff.service;

import org.dromara.business.staff.domain.vo.MpStaffHomeVo;

/**
 * mp 员工端服务（小程序工作台，详设 4.6）
 *
 * <p>鉴权口径：token 内 employeeId（管理用户ID）非空才可访问；每次请求以识别口径
 * 实时复检（存在 + 启用 + 部门与当前门店一致），管理员停用/调岗后旧 token 立即摘除员工能力。</p>
 *
 * @author store-ops
 */
public interface IMpStaffService {

    /**
     * 工作台首页数据（门店名 + 员工名）
     *
     * @param memberId   会员ID（token 换出）
     * @param employeeId 员工身份ID（token 换出，非员工为 null）
     * @return 首页数据
     */
    MpStaffHomeVo home(Long memberId, Long employeeId);

}
