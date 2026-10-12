package org.dromara.business.member.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.business.member.domain.BizPointLog;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 会员积分流水视图对象 biz_point_log
 *
 * @author store-ops
 */
@Data
@AutoMapper(target = BizPointLog.class)
public class BizPointLogVo implements Serializable {

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
     * 变动积分（正获得/负消耗）
     */
    private BigDecimal changePoint;

    /**
     * 变动后积分快照
     */
    private BigDecimal afterPoint;

    /**
     * 业务类型（1消费获得 2兑换消耗，字典 point_log_type）
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

    /**
     * 流水时间
     */
    private Date createTime;

}
