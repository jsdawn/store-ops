package org.dromara.business.member.service;

import org.dromara.business.member.domain.bo.MpBindMobileBo;
import org.dromara.business.member.domain.vo.MpLoginVo;
import org.dromara.business.member.domain.vo.MpMemberVo;
import org.dromara.business.member.domain.vo.MpStoreBriefVo;

import java.util.List;

/**
 * mp 端会员服务接口（顾客微信端，详设 4.3 / 5.1）
 *
 * <p>与员工端 {@link IBizMemberService} 分列：mp 链路无登录态，
 * 租户上下文由服务内部按门店解析（TenantHelper.dynamic），归属校验全部显式比对。</p>
 *
 * @author store-ops
 */
public interface IMpMemberService {

    /**
     * 确认手机号：按（当前店, 手机号）匹配 → 已有档案补绑/覆盖 open_id；无档案新建；
     * 完成后签发 mp 端 token 并返回会员信息（登录闭环，前端不再二次调 /auth/mp/login）
     *
     * @param bo 绑定入参（storeId + openId + mobile + nickName）
     * @return 登录结果（token + 会员信息）
     */
    MpLoginVo bindMobile(MpBindMobileBo bo);

    /**
     * 查询会员档案（mp token 校验通过后调用）
     *
     * @param memberId   会员ID（token 换出）
     * @param storeId    当前店ID（显式归属比对，不一致抛业务异常）
     * @return 会员信息（含门店名）
     */
    MpMemberVo queryMember(Long memberId, Long storeId);

    /**
     * 我的门店列表：按（open_id 命中，或手工建档 open_id 为空且手机号命中）跨租户
     * 查会员档案 join 营业中门店，带资产摘要（切换门店弹层数据源）
     *
     * @param memberId 会员ID（token 换出，取 open_id/mobile 作查询口径）
     * @return 门店摘要列表（当前店置顶并标记 current）
     */
    List<MpStoreBriefVo> listStores(Long memberId);

    /**
     * 切换门店：按（目标店, open_id/mobile 同口径）查档案 → 命中重签该店 token
     * （token 绑定档案ID，切店必须换签）；未命中抛业务异常（列表仅含已注册门店，正常不触达）
     *
     * @param memberId      会员ID（token 换出）
     * @param targetStoreId 目标门店ID
     * @return 登录结果（新店 token + 会员信息）
     */
    MpLoginVo switchStore(Long memberId, Long targetStoreId);

}
