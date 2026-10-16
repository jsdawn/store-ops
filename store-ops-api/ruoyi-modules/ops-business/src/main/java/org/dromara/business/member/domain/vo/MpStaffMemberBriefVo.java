package org.dromara.business.member.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 员工端会员精简视图（工作台选会员弹层行，设计稿 39:626）
 *
 * <p>口径对齐管理台收银选会员（详设 4.2 #4）：昵称、手机号、卡号、余额、积分。</p>
 *
 * @author store-ops
 */
@Data
public class MpStaffMemberBriefVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会员ID（雪花，序列化字符串下发）
     */
    private Long memberId;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 手机号
     */
    private String mobile;

    /**
     * 会员卡号
     */
    private String memberNo;

    /**
     * 余额（元）
     */
    private BigDecimal balance;

    /**
     * 积分余额
     */
    private BigDecimal pointBalance;

}
