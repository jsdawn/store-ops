import { http } from '@/http/http'
import { getEnvBaseUrl } from '@/utils'

/**
 * auth 网关基址：baseURL 内置的 /mp 是 ops-business 业务路由前缀（/mp/** 不剥前缀直达），
 * 登录接口走 /auth/** 路由（→ ruoyi-auth，StripPrefix 1），需剥掉 /mp 回到网关根。
 * 注：绝对 URL 会绕过 H5 dev 代理（interceptor 仅对非 http 开头拼接），mp-weixin 为交付端不受影响。
 */
const authBase = getEnvBaseUrl().replace(/\/mp$/, '')

/**
 * mp 端会员信息（对应后端 MpMemberVo）
 * 注意：雪花ID（memberId/storeId）后端经 BigNumberSerializer 以字符串下发，
 * 前端必须用 string 承接，禁止转 number（超 Number.MAX_SAFE_INTEGER 精度丢失）。
 */
export interface IMpMember {
  memberId: string
  storeId: string
  memberNo: string
  /** 微信open_id（/member/me 回带，供编辑页自查补齐会话态） */
  openId?: string
  mobile?: string
  nickName?: string
  avatar?: string
  balance?: number
  pointBalance?: number
  storeName?: string
  /** 员工身份 = 管理用户ID（会员被识别为当前门店员工时下发，决策 D9）；雪花字符串 */
  employeeId?: string
  /** 员工姓名（工作台「店员：xxx」展示） */
  employeeName?: string
}

/** mp 端登录会话（token + 会员档案，对应后端 MpLoginVo.member 部分） */
export interface IMpSession {
  token: string
  expiresIn: number
  member: IMpMember
}

/**
 * /auth/mp/login 返回：命中档案 → token + member；未命中 → needBindMobile + openId
 */
export interface IMpLoginRes extends Partial<IMpSession> {
  needBindMobile?: boolean
  openId?: string
}

/**
 * mp 端登录：wx.login code 换 open_id，按（租户, 门店, open_id）查档案（详设 4.5）
 * @param data code + storeId（扫码 scene）
 */
export function mpLogin(data: { code: string, storeId: string }) {
  // 实际请求：{authBase}/auth/mp/login（网关 /auth/** → ruoyi-auth），不能带业务前缀 /mp
  return http.post<IMpLoginRes>(`${authBase}/auth/mp/login`, data)
}

/**
 * 手机号快速验证 code 解码：open-type="getPhoneNumber" 回调 code → 真实手机号
 * 路径说明同 mpLogin：走 /auth/** 路由（→ ruoyi-auth），相对网关根拼接
 * @param data getPhoneNumber 回调的 code（5 分钟有效、单次使用）
 */
export function decodePhone(data: { code: string }) {
  return http.post<{ phone: string }>(`${authBase}/auth/mp/phone`, data)
}

/**
 * 确认手机号：按（当前店, 手机号）匹配补绑/新建档案，重颁 token（登录闭环）
 * 路径说明：baseURL 已含网关业务前缀 /mp（/mp/** → ops-business 不剥前缀，
 * controller 自带 /mp/member 映射），此处相对路径只写 /member/...
 * @param data storeId + openId + mobile + nickName
 */
export function bindMobile(data: {
  storeId: string
  openId: string
  mobile: string
  nickName: string
  avatar?: string
}) {
  return http.post<IMpSession>('/member/bind-mobile', data)
}

/**
 * mp 端门店摘要（我的门店列表行，对应后端 MpStoreBriefVo）
 * storeId 雪花以字符串承接（同 IMpMember 约定）
 */
export interface IMpStoreBrief {
  storeId: string
  storeName: string
  memberNo: string
  balance?: number
  pointBalance?: number
  /** 是否当前门店 */
  current?: boolean
}

/**
 * 我的门店列表：open_id/手机号跨租户查已注册（营业中）门店（切换门店弹层数据源）
 * 路径说明同 bind-mobile：baseURL 已含 /mp，相对路径不重复带前缀
 */
export function getMyStores() {
  return http.get<IMpStoreBrief[]>('/member/stores')
}

/**
 * 切换门店：后端按目标店档案重签 token（token 绑定档案ID）
 * @param storeId 目标门店ID（须为已注册会员的门店）
 */
export function switchStore(storeId: string) {
  return http.post<IMpSession>('/member/switch-store', { storeId })
}

/**
 * 当前店资产：卡号、昵称、余额、积分、门店名（header Mp-Token 鉴权）
 * 路径说明同 bind-mobile：baseURL 已含 /mp，相对路径不重复带前缀
 * @param storeId 当前店ID（显式携带，后端做归属比对）
 */
export function getMe(storeId: string) {
  return http.get<IMpMember>('/member/me', { storeId })
}

/** mp 员工端工作台首页数据（对应后端 MpStaffHomeVo） */
export interface IMpStaffHome {
  storeId: string
  storeName: string
  staffName: string
}

/**
 * 员工工作台首页：门店名 + 员工名（token 需带员工身份，服务层实时复检，详设 4.6）
 * 路径说明同 bind-mobile：baseURL 已含 /mp，相对路径不重复带前缀
 */
export function getStaffHome() {
  return http.get<IMpStaffHome>('/staff/home')
}
