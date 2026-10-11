import type { TabBar } from '@uni-helper/vite-plugin-uni-pages'
import type { CustomTabBarItem, NativeTabBarItem } from './types'

/**
 * tabbar 选择的策略，更详细的介绍见 tabbar.md 文件
 * 0: 'NO_TABBAR' `无 tabbar`
 * 1: 'NATIVE_TABBAR'  `原生 tabbar`
 * 2: 'CUSTOM_TABBAR' `自定义 tabbar`
 *
 * 温馨提示：本文件的任何代码更改了之后，都需要重新运行，否则 pages.json 不会更新导致配置不生效
 */
export const TABBAR_STRATEGY_MAP = {
  NO_TABBAR: 0,
  NATIVE_TABBAR: 1,
  CUSTOM_TABBAR: 2,
}

// store-ops 使用微信原生 tabbar：C 端顾客页签固定（会员中心/积分中心），无需按角色动态变更
export const selectedTabbarStrategy = TABBAR_STRATEGY_MAP.NATIVE_TABBAR

// 原生 tabbar 配置：iconPath 可选，不配即纯文字（业务图标后补）
export const nativeTabbarList: NativeTabBarItem[] = [
  {
    pagePath: 'pages/member/member',
    text: '会员中心',
  },
  {
    pagePath: 'pages/point/point',
    text: '积分中心',
  },
]

// 自定义 tabbar 备用配置（当前未启用），页签与 nativeTabbarList 保持一致
export const customTabbarList: CustomTabBarItem[] = [
  {
    pagePath: 'pages/member/member',
    text: '会员中心',
    iconType: 'unocss',
    icon: 'i-carbon-user',
  },
  {
    pagePath: 'pages/point/point',
    text: '积分中心',
    iconType: 'unocss',
    icon: 'i-carbon-star',
  },
]

/**
 * 是否启用 tabbar 缓存
 * NATIVE_TABBAR(1) 和 CUSTOM_TABBAR(2) 时，需要tabbar缓存
 */
export const tabbarCacheEnable
  = [TABBAR_STRATEGY_MAP.NATIVE_TABBAR, TABBAR_STRATEGY_MAP.CUSTOM_TABBAR].includes(selectedTabbarStrategy)

/**
 * 是否启用自定义 tabbar
 * CUSTOM_TABBAR(2) 时，启用自定义tabbar
 */
export const customTabbarEnable = [TABBAR_STRATEGY_MAP.CUSTOM_TABBAR].includes(selectedTabbarStrategy)

/**
 * 是否需要隐藏原生 tabbar
 * CUSTOM_TABBAR(2) 时，需要隐藏原生tabbar
 */
export const needHideNativeTabbar = selectedTabbarStrategy === TABBAR_STRATEGY_MAP.CUSTOM_TABBAR

const _tabbarList = customTabbarEnable ? customTabbarList.map(item => ({ text: item.text, pagePath: item.pagePath })) : nativeTabbarList
export const tabbarList = customTabbarEnable ? customTabbarList : nativeTabbarList

const _tabbar: TabBar = {
  // 只有微信小程序支持 custom。App 和 H5 不生效
  custom: selectedTabbarStrategy === TABBAR_STRATEGY_MAP.CUSTOM_TABBAR,
  color: '#999999',
  // store-ops 鲜橙主色
  selectedColor: '#FF7A29',
  backgroundColor: '#F8F8F8',
  borderStyle: 'black',
  height: '50px',
  fontSize: '10px',
  iconWidth: '24px',
  spacing: '3px',
  list: _tabbarList as unknown as TabBar['list'],
}

export const tabBar = tabbarCacheEnable ? _tabbar : {}
