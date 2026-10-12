package org.dromara.business.member.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.business.member.domain.BizMember;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 会员视图对象 biz_member
 *
 * @author store-ops
 */
@Data
@AutoMapper(target = BizMember.class)
public class BizMemberVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 门店名称（store 域门面批量补齐）
     */
    private String storeName;

    /**
     * 冗余门店绑定部门ID
     */
    private Long deptId;

    /**
     * 会员卡号（门店内唯一）
     */
    private String memberNo;

    /**
     * 手机号（前端展示层脱敏 138****6688）
     */
    private String mobile;

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
     * 累计消费（元）
     */
    private BigDecimal totalAmount;

    /**
     * 累计消费次数
     */
    private Integer totalOrders;

    /**
     * 微信open_id（仅服务端内部用于计算 wxBound，不对外输出）
     */
    @JsonIgnore
    private String openId;

    /**
     * 微信绑定状态（open_id 非空即已绑定；服务端填充）
     */
    private Boolean wxBound;

    /**
     * 备注
     */
    private String remark;

    /**
     * 开卡时间
     */
    private Date createTime;

}
