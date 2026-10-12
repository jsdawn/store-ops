import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { MemberForm, MemberQuery, MemberVO, RechargeForm, RechargeLogQuery, RechargeLogVO } from './types';

// 查询会员列表
export function listMember(query: MemberQuery): AxiosPromise<MemberVO[]> {
  return request({
    url: '/business/member/list',
    method: 'get',
    params: query
  });
}

// 查询会员详细
export function getMember(memberId: string | number): AxiosPromise<MemberVO> {
  return request({
    url: '/business/member/' + memberId,
    method: 'get'
  });
}

// 线下登记会员（open_id 留空，顾客日后微信登录自动补绑合并）
export function addMember(data: MemberForm) {
  return request({
    url: '/business/member',
    method: 'post',
    data: data
  });
}

// 充值入账（线下收款记账式，仅记录不触发支付）
export function rechargeMember(data: RechargeForm) {
  return request({
    url: '/business/member/recharge',
    method: 'post',
    data: data
  });
}

// 查询充值流水分页
export function listRechargeLog(query: RechargeLogQuery): AxiosPromise<RechargeLogVO[]> {
  return request({
    url: '/business/member/recharge/list',
    method: 'get',
    params: query
  });
}

// 充值冲正（仅当日、仅一次、余额充足）
export function reversalRecharge(rechargeId: string | number, reason: string) {
  return request({
    url: `/business/member/recharge/${rechargeId}/reversal`,
    method: 'post',
    data: { reason }
  });
}
