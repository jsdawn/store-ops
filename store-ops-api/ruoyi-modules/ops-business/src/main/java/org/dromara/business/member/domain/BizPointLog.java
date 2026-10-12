package org.dromara.business.member.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 会员积分流水对象 biz_point_log（余额+流水模式）
 *
 * @author store-ops
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_point_log")
public class BizPointLog extends TenantEntity {

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
     * 变动积分（正获得/负消耗）
     */
    private BigDecimal changePoint;

    /**
     * 变动后积分快照
     */
    private BigDecimal afterPoint;

    /**
     * 业务类型（1消费获得 2兑换消耗）
     */
    private String bizType;

    /**
     * 关联业务单号（订单号/券实例ID）
     */
    private String bizNo;

    /**
     * 备注
     */
    private String remark;

}
