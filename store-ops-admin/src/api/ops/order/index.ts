import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { OrderQuery, OrderVO } from './types';

// 查询订单分页（筛选：门店/状态/支付方式/订单号/手机号/时间区间）
export function listOrder(query: OrderQuery): AxiosPromise<OrderVO[]> {
  return request({
    url: '/business/order/list',
    method: 'get',
    params: query
  });
}

// 查询订单详情（含明细行）
export function getOrder(orderId: string | number): AxiosPromise<OrderVO> {
  return request({
    url: '/business/order/' + orderId,
    method: 'get'
  });
}

// 作废订单（仅待收款、原因必填；仅店长权限）
export function voidOrder(orderId: string | number, reason: string) {
  return request({
    url: `/business/order/${orderId}/void`,
    method: 'post',
    data: { reason }
  });
}

// 追加订单备注（仅已完成，错账备注用）
export function remarkOrder(orderId: string | number, remark: string) {
  return request({
    url: `/business/order/${orderId}/remark`,
    method: 'put',
    data: { remark }
  });
}

// 余额收款错账冲正（仅已完成+余额收款+当日+未冲正过）
export function reversalOrderBalance(orderId: string | number, reason: string) {
  return request({
    url: `/business/order/${orderId}/balance-reversal`,
    method: 'post',
    data: { reason }
  });
}
