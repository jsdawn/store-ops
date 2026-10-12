package org.dromara.business.member.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 充值流水对象 biz_recharge_log（冲正状态挂本表，冲正动作另记余额流水）
 *
 * @author store-ops
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_recharge_log")
public class BizRechargeLog extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 充值流水ID（雪花）
     */
    @TableId(value = "recharge_id")
    private Long rechargeId;

    /**
     * 充值流水号（RC + yyMMdd + 雪花后6位，全局唯一）
     */
    private String rechargeNo;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 冗余门店绑定部门ID（数据权限过滤）
     */
    private Long deptId;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * 充值金额（本金）
     */
    private BigDecimal amount;

    /**
     * 赠送金额
     */
    private BigDecimal giftAmount;

    /**
     * 充值方式（1微信 2支付宝 3现金，仅记录不触发支付）
     */
    private String payType;

    /**
     * 操作店员（sys_user.user_id）
     */
    private Long operatorId;

    /**
     * 操作店员姓名（冗余，防员工离职后不可读）
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

}
