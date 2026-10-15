package org.dromara.business.order.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.business.order.domain.BizOrderItem;
import org.dromara.business.order.domain.vo.BizOrderItemVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

/**
 * 订单明细 数据层
 *
 * @author store-ops
 */
public interface BizOrderItemMapper extends BaseMapperPlus<BizOrderItem, BizOrderItemVo> {

    /**
     * 按订单查明细行（落库顺序，即录入顺序）
     */
    default List<BizOrderItemVo> selectByOrderId(Long orderId) {
        return this.selectVoList(new LambdaQueryWrapper<BizOrderItem>()
            .eq(BizOrderItem::getOrderId, orderId)
            .orderByAsc(BizOrderItem::getItemId));
    }

}
