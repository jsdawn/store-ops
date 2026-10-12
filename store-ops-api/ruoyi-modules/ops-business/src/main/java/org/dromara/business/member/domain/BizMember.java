package org.dromara.business.member.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 会员对象 biz_member（门店级会员账户本体：档案 + 余额 + 积分 + 累计消费冗余）
 *
 * @author store-ops
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_member")
public class BizMember extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会员ID（雪花）
     */
    @TableId(value = "member_id")
    private Long memberId;

    /**
     * 门店ID（各店独立会员池）
     */
    private Long storeId;

    /**
     * 冗余门店绑定部门ID（数据权限过滤）
     */
    private Long deptId;

    /**
     * 会员卡号（门店内唯一）
     */
    private String memberNo;

    /**
     * 手机号（建档必填，门店内唯一）
     */
    private String mobile;

    /**
     * 微信open_id（仅登录凭证，未绑定为 NULL）
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

    /**
     * 累计消费（元，已完成订单应付合计）
     */
    private BigDecimal totalAmount;

    /**
     * 累计消费次数
     */
    private Integer totalOrders;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

}
