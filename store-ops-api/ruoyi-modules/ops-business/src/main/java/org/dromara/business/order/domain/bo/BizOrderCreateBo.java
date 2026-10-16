package org.dromara.business.order.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.util.List;

/**
 * 开单入参 biz_order（→待收款；行金额与订单总额服务端计算，前端合计仅作展示）
 *
 * @author store-ops
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BizOrderCreateBo extends BaseEntity {

    /**
     * 门店ID（开单所属门店）
     */
    @NotNull(message = "门店不能为空")
    private Long storeId;

    /**
     * 会员ID（散客不传）
     */
    private Long memberId;

    /**
     * 消费明细（1~50 行）
     */
    @Valid
    @NotEmpty(message = "消费明细不能为空")
    @Size(max = 50, message = "消费明细最多{max}行")
    private List<BizOrderItemBo> items;

}
