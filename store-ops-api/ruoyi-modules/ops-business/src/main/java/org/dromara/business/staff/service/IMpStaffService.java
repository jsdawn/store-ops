package org.dromara.business.staff.service;

import org.dromara.business.member.domain.bo.MpStaffRechargeBo;
import org.dromara.business.member.domain.vo.MpStaffMemberBriefVo;
import org.dromara.business.staff.domain.vo.MpStaffHomeVo;

import java.util.List;

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

    /**
     * 本店会员搜索（充值/收银选会员弹层，详设 4.2 #4 同口径）：keyword 手机号/卡号/昵称模糊，前 20 条
     *
     * @param memberId   会员ID（token 换出，用于定位员工所属门店）
     * @param employeeId 员工身份ID（token 换出）
     * @param keyword    搜索关键字（可空，空返回最近档案）
     * @return 会员精简列表
     */
    List<MpStaffMemberBriefVo> searchMembers(Long memberId, Long employeeId, String keyword);

    /**
     * 签发充值提交凭证（Redis 一次有效，5 分钟，详设 5.2 校验链第 4 步）
     *
     * @param memberId   会员ID（token 换出）
     * @param employeeId 员工身份ID（token 换出）
     * @return 提交凭证
     */
    String issueRechargeToken(Long memberId, Long employeeId);

    /**
     * 充值入账（员工端）：目标会员必须属当前门店；入账/流水复用 member 域共用内核（详设 5.2）
     *
     * @param bo         充值入参（含一次性提交凭证）
     * @param memberId   会员ID（token 换出，定位员工所属门店）
     * @param employeeId 员工身份ID（token 换出，操作人）
     */
    void recharge(MpStaffRechargeBo bo, Long memberId, Long employeeId);

}
