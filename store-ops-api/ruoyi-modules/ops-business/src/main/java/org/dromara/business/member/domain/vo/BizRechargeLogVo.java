package org.dromara.business.member.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.business.member.domain.BizRechargeLog;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 充值流水视图对象 biz_recharge_log
 *
 * @author store-ops
 */
@Data
@AutoMapper(target = BizRechargeLog.class)
public class BizRechargeLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 充值流水ID
     */
    private Long rechargeId;

    /**
     * 充值流水号（RC + yyMMdd + 雪花后6位）
     */
    private String rechargeNo;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 门店名称（store 域门面批量补齐）
     */
    private String storeName;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * 会员昵称（member 域批量补齐）
     */
    private String nickName;

    /**
     * 会员手机号（member 域批量补齐，前端脱敏）
     */
    private String mobile;

    /**
     * 充值金额（本金）
     */
    private BigDecimal amount;

    /**
     * 赠送金额
     */
    private BigDecimal giftAmount;

    /**
     * 到账合计（amount + giftAmount，计算列）
     */
    private BigDecimal totalAmount;

    /**
     * 充值方式（1微信 2支付宝 3现金，字典 member_pay_type）
     */
    private String payType;

    /**
     * 操作店员ID
     */
    private Long operatorId;

    /**
     * 操作店员姓名（冗余）
     */
    private String operatorName;

    /**
     * 状态（0有效 1已冲正）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 充值时间
     */
    private Date createTime;

}
