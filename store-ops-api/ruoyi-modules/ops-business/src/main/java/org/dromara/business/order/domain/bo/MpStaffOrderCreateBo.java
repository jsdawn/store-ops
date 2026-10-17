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
 * mp 员工端开单入参（收银开单页，→待收款）
 *
 * <p>storeId 不传：由服务层按员工所属门店解析（token 换出 member → 门店）；
 * operator 不传：服务层填员工身份（管理用户，决策 D11）。行金额与订单总额服务端计算（详设 5.1）。</p>
 *
 * @author store-ops
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MpStaffOrderCreateBo extends BaseEntity {

    /**
     * 会员ID（散客不传）
     */
    private Long memberId;

    /**
     * 消费明细（1~50 行，复用管理台行入参校验口径）
     */
    @Valid
    @NotEmpty(message = "消费明细不能为空")
    @Size(max = 50, message = "消费明细最多{max}行")
    private List<BizOrderItemBo> items;

    /**
     * 开单提交凭证（Redis 一次有效，防弱网重复开单，与充值凭证同构）
     */
    @NotNull(message = "提交凭证不能为空")
    private String orderToken;

}
