package org.dromara.business.member.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 端会员精简视图对象（顾客端出参，不吐 dept_id / total 明细等内部字段，详设 3.4）
 *
 * @author store-ops
 */
@Data
public class MpMemberVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * 门店ID（当前档案归属店）
     */
    private Long storeId;

    /**
     * 会员卡号
     */
    private String memberNo;

    /**
     * 手机号
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
     * 门店名称（当前店）
     */
    private String storeName;

}
