import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { StoreDeptOption, StoreForm, StoreQrcodeVO, StoreQuery, StoreVO } from './types';

// 查询门店列表
export function listStore(query: StoreQuery): AxiosPromise<StoreVO[]> {
  return request({
    url: '/business/store/list',
    method: 'get',
    params: query
  });
}

// 查询门店详细
export function getStore(storeId: string | number): AxiosPromise<StoreVO> {
  return request({
    url: '/business/store/' + storeId,
    method: 'get'
  });
}

// 新增门店
export function addStore(data: StoreForm) {
  return request({
    url: '/business/store',
    method: 'post',
    data: data
  });
}

// 修改门店（部门只读，不传 deptId）
export function updateStore(data: StoreForm) {
  return request({
    url: '/business/store',
    method: 'put',
    data: data
  });
}

// 门店启停切换
export function changeStoreStatus(storeId: string | number, status: string) {
  return request({
    url: '/business/store/changeStatus',
    method: 'put',
    data: { storeId, status }
  });
}

// 未绑定门店的部门下拉（新增数据源）
export function listUnboundDepts(): AxiosPromise<StoreDeptOption[]> {
  return request({
    url: '/business/store/unbound-depts',
    method: 'get'
  });
}

// 门店精简下拉（仅启用门店）
export function optionselect(): AxiosPromise<StoreVO[]> {
  return request({
    url: '/business/store/optionselect',
    method: 'get'
  });
}

// 门店小程序码（P1 占位：返回 scene）
export function getStoreQrcode(storeId: string | number): AxiosPromise<StoreQrcodeVO> {
  return request({
    url: `/business/store/${storeId}/qrcode`,
    method: 'get'
  });
}
