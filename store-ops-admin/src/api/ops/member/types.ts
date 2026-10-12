export interface MemberVO extends BaseEntity {
  memberId: number | string;
  storeId: number | string;
  storeName: string;
  deptId: number | string;
  memberNo: string;
  mobile: string;
  nickName: string;
  avatar: string;
  balance: number;
  pointBalance: number;
  totalAmount: number;
  totalOrders: number;
  wxBound: boolean;
  remark: string;
}

export interface MemberForm {
  storeId: number | string | undefined;
  mobile: string;
  nickName: string;
  remark: string;
}

export interface MemberQuery extends PageQuery {
  storeId: number | string | '';
  mobile: string;
  memberNo: string;
  nickName: string;
}

export interface RechargeForm {
  memberId: number | string;
  memberNo: string;
  nickName: string;
  mobile: string;
  storeName: string;
  amount: number | undefined;
  giftAmount: number | undefined;
  payType: string;
  remark: string;
}

export interface RechargeLogVO extends BaseEntity {
  rechargeId: number | string;
  rechargeNo: string;
  storeId: number | string;
  storeName: string;
  memberId: number | string;
  nickName: string;
  mobile: string;
  amount: number;
  giftAmount: number;
  totalAmount: number;
  payType: string;
  operatorId: number | string;
  operatorName: string;
  status: string;
  remark: string;
  createTime: string;
}

export interface RechargeLogQuery extends PageQuery {
  storeId: number | string | '';
  mobile: string;
  nickName: string;
  payType: string;
  status: string;
  beginTime: string;
  endTime: string;
}
