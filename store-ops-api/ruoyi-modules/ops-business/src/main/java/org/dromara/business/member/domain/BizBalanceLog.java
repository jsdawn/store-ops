package org.dromara.business.member.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 会员余额流水对象 biz_balance_log（每笔变动一条，含变动后快照）
 *
 * @author store-ops
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_balance_log")
public class BizBalanceLog extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 流水ID（雪花）
     */
    @TableId(value = "log_id")
    private Long logId;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 冗余门店绑定部门ID
     */
    private Long deptId;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * 变动金额（正入账/负扣减）
     */
    private BigDecimal changeAmount;

    /**
     * 变动后余额快照（对账用）
     */
    private BigDecimal afterBalance;

    /**
     * 业务类型（1充值 2消费 3冲正）
     */
    private String bizType;

    /**
     * 关联业务单号（充值流水号/订单号）
     */
    private String bizNo;

    /**
     * 备注（冲正原因记此处）
     */
    private String remark;

}
