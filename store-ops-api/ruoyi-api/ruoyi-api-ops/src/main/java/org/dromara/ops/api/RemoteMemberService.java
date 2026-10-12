package org.dromara.ops.api;

import org.dromara.ops.api.domain.vo.RemoteMemberVo;

/**
 * 会员远程服务（ruoyi-auth mp 登录链路跨服务查询，提供方：ops-business）
 *
 * <p>mp 端请求无登录态，租户插件不自动过滤，实现侧须按入参 tenantId
 * 以 TenantHelper.dynamic 显式包裹；所有查询显式比对 storeId（门店级隔离，PRD 6.1）。</p>
 *
 * @author store-ops
 */
public interface RemoteMemberService {

    /**
     * 按（租户, 门店, open_id）查会员档案
     *
     * @param tenantId 租户ID
     * @param storeId  门店ID
     * @param openId   微信open_id
     * @return 会员档案，未命中返回 null
     */
    RemoteMemberVo selectByOpenId(String tenantId, Long storeId, String openId);

    /**
     * 按（租户, 门店, 手机号）查会员档案
     *
     * @param tenantId 租户ID
     * @param storeId  门店ID
     * @param mobile   手机号
     * @return 会员档案，未命中返回 null
     */
    RemoteMemberVo selectByMobile(String tenantId, Long storeId, String mobile);

    /**
     * 补绑/覆盖 open_id（mp 确认手机号链路）
     *
     * <p>新 open_id 已被同店另一档案占用时抛业务异常（uk_tenant_store_open 兜底，E5/T2 本期人工处理）。</p>
     *
     * @param tenantId 租户ID
     * @param memberId 会员ID
     * @param openId   微信open_id
     */
    void bindOpenId(String tenantId, Long memberId, String openId);

    /**
     * mp 端建档（含卡号生成，open_id + mobile）
     *
     * @param tenantId 租户ID
     * @param storeId  门店ID
     * @param mobile   手机号（本店唯一）
     * @param openId   微信open_id
     * @param nickName 微信昵称（可空）
     * @param avatar   微信头像（可空）
     * @return 新建档案
     */
    RemoteMemberVo registerMember(String tenantId, Long storeId, String mobile,
                                  String openId, String nickName, String avatar);

}
