package org.dromara.business.member.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 充值流水业务对象 biz_recharge_log（分页筛选入参）
 *
 * @author store-ops
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BizRechargeLogBo extends BaseEntity {

    /**
     * 门店ID（筛选）
     */
    private Long storeId;

    /**
     * 会员手机号（模糊筛选，服务端先解析为会员ID集合）
     */
    private String mobile;

    /**
     * 会员昵称（模糊筛选）
     */
    private String nickName;

    /**
     * 充值方式（筛选）
     */
    private String payType;

    /**
     * 状态（0有效 1已冲正，筛选）
     */
    private String status;

    /**
     * 充值时间起
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date beginTime;

    /**
     * 充值时间止
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

}
