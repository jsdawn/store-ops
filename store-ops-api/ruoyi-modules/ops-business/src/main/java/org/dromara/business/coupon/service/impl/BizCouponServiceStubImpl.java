package org.dromara.business.coupon.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.business.coupon.domain.vo.CouponOptionVo;
import org.dromara.business.coupon.service.IBizCouponService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 优惠券 stub 实现（券域 P3 前占位，详设 4.4 / D3）
 *
 * <p>候选恒空、核销拒绝；order 域代码已按真实语义编排调用，
 * 券域落地后以同名实现替换本类，order 域零改动接入。</p>
 *
 * @author store-ops
 */
@RequiredArgsConstructor
@Service
public class BizCouponServiceStubImpl implements IBizCouponService {

    @Override
    public List<CouponOptionVo> listUsableForOrder(Long memberId, Long storeId, BigDecimal totalAmount) {
        // 券域未上线：返回空列表，前端显示「优惠券功能上线后开放」
        return List.of();
    }

    @Override
    public boolean writeOff(Long couponId, Long orderId) {
        throw new ServiceException("优惠券功能上线后开放");
    }

}
