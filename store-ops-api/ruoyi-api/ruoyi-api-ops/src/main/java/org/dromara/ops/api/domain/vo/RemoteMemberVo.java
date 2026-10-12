package org.dromara.ops.api.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 会员档案远程视图对象（跨服务传输，biz_member）
 *
 * <p>供 ruoyi-auth mp 登录链路等跨服务场景使用；查询方需显式携带租户/门店维度，
 * 服务端实现按 TenantHelper.dynamic 隔离（mp 请求无登录态、租户插件不自动过滤）。</p>
 *
 * @author store-ops
 */
@Data
public class RemoteMemberVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会员ID（雪花，租户/门店全局唯一）
     */
    private Long memberId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 冗余门店绑定部门ID（数据权限过滤用）
     */
    private Long deptId;

    /**
     * 会员卡号（门店内唯一）
     */
    private String memberNo;

    /**
     * 手机号
     */
    private String mobile;

    /**
     * 微信open_id（未绑定为 null）
     */
    private String openId;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 微信头像URL
     */
    private String avatar;

    /**
     * 余额（元）
     */
    private BigDecimal balance;

    /**
     * 积分（两位小数）
     */
    private BigDecimal pointBalance;

}
