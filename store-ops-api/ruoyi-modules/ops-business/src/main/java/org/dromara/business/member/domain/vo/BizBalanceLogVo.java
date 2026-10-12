package org.dromara.business.member.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.business.member.domain.BizBalanceLog;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 会员余额流水视图对象 biz_balance_log
 *
 * @author store-ops
 */
@Data
@AutoMapper(target = BizBalanceLog.class)
public class BizBalanceLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 流水ID
     */
    private Long logId;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * 变动金额（正入账/负扣减）
     */
    private BigDecimal changeAmount;

    /**
     * 变动后余额快照
     */
    private BigDecimal afterBalance;

    /**
     * 业务类型（1充值 2消费 3冲正，字典 balance_log_type）
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

    /**
     * 流水时间
     */
    private Date createTime;

}
