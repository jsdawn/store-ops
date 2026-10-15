package org.dromara.business.order.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 订单查询入参 biz_order（列表筛选；GET 参数绑定需 @DateTimeFormat，同充值流水口径）
 *
 * @author store-ops
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BizOrderQueryBo extends BaseEntity {

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 状态（order_status：0待收款 1已完成 2已作废）
     */
    private String status;

    /**
     * 支付方式（order_pay_type：1余额 2微信 3支付宝 4现金）
     */
    private String payType;

    /**
     * 订单号（模糊）
     */
    private String orderNo;

    /**
     * 会员手机号（快照列，模糊）
     */
    private String memberMobile;

    /**
     * 开单时间起
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date beginTime;

    /**
     * 开单时间止
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

}
