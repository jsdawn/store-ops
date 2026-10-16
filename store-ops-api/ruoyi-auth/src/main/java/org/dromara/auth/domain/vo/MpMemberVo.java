package org.dromara.auth.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 端会员登录视图对象（顾客端出参）
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
     * 门店ID
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
     * 员工身份 = 管理用户ID（会员被识别为当前门店员工时下发，决策 D9）；非员工为 null
     */
    private Long employeeId;

    /**
     * 员工姓名（管理用户昵称，工作台「店员：xxx」展示用）
     */
    private String employeeName;

}
