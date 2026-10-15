export interface OrderItemVO {
  itemId: number | string;
  itemName: string;
  unitPrice: number;
  quantity: number;
  lineAmount: number;
}

export interface OrderVO extends BaseEntity {
  orderId: number | string;
  orderNo: string;
  storeId: number | string;
  storeName: string;
  memberId: number | string | null;
  memberName: string;
  memberMobile: string;
  totalAmount: number;
  couponId: number | string | null;
  couponName: string;
  couponAmount: number;
  payAmount: number;
  payType: string;
  status: string;
  earnedPoint: number;
  balanceReversed: string;
  voidReason: string;
  operatorName: string;
  remark: string;
  createTime: string;
  items: OrderItemVO[];
}

export interface OrderQuery extends PageQuery {
  storeId: number | string | '';
  status: string;
  payType: string;
  orderNo: string;
  memberMobile: string;
  beginTime: string;
  endTime: string;
}
