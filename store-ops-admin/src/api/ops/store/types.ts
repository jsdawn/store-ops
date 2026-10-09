export interface StoreVO extends BaseEntity {
  storeId: number | string;
  deptId: number | string;
  deptName: string;
  storeName: string;
  address: string;
  status: string;
  remark: string;
}

export interface StoreForm {
  storeId: number | string | undefined;
  deptId: number | string | undefined;
  storeName: string;
  address: string;
  status: string;
  remark: string;
}

export interface StoreQuery extends PageQuery {
  storeName: string;
  status: string;
}

export interface StoreDeptOption {
  deptId: number | string;
  deptName: string;
  parentId: number | string;
}

export interface StoreQrcodeVO {
  storeId: number | string;
  storeName: string;
  scene: string;
  imageUrl: string | null;
  tip: string;
}
