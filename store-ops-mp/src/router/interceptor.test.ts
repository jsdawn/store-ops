import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * store.ts 的 userRoles 是模块级 computed，会缓存对某个 pinia 实例的依赖；
 * test-setup 每个用例都会换一个新的 pinia，所以这里必须一起重置模块，
 * 否则第二个用例读到的是上一个 pinia 的缓存值。
 */
beforeEach(() => {
  vi.resetModules()
})

async function invoke(url: string, userInfo?: { roles?: string[] }) {
  if (userInfo) {
    const { useUserStore } = await import('@/store/user')
    useUserStore().setUserInfo({ userId: 1, username: 'u', nickname: 'U', ...userInfo })
  }
  const { navigateToInterceptor } = await import('./interceptor')
  return navigateToInterceptor.invoke({ url })
}

// mock 一份 tabbar 配置：会员中心所有人可见，积分中心仅 admin 可见
describe('navigateToInterceptor 角色守卫', () => {
  beforeEach(() => {
    vi.doMock('@/tabbar/config', () => ({
      TABBAR_STRATEGY_MAP: { NO_TABBAR: 0, NATIVE_TABBAR: 1, CUSTOM_TABBAR: 2 },
      selectedTabbarStrategy: 2,
      tabbarList: [
        { text: '会员中心', pagePath: 'pages/member/member', iconType: 'unocss', icon: 'i-carbon-user' },
        { text: '积分中心', pagePath: 'pages/point/point', iconType: 'unocss', icon: 'i-carbon-star', roles: ['admin'] },
      ],
    }))
  })

  it('角色不足时阻止进入受限 tabbar 页，并回退到可见的 tabbar 页', async () => {
    const result = await invoke('/pages/point/point')

    expect(result).toBe(false)
    expect(uni.reLaunch).toHaveBeenCalledWith({ url: '/pages/member/member' })
  })

  it('角色满足时放行', async () => {
    const result = await invoke('/pages/point/point', { roles: ['admin'] })

    expect(result).not.toBe(false)
    expect(uni.reLaunch).not.toHaveBeenCalled()
  })

  it('未配置 roles 的 tabbar 页放行', async () => {
    const result = await invoke('/pages/member/member')

    expect(result).not.toBe(false)
    expect(uni.reLaunch).not.toHaveBeenCalled()
  })

  it('首页未被角色限制时，首屏 "/" 放行', async () => {
    const result = await invoke('/')

    expect(result).not.toBe(false)
    expect(uni.reLaunch).not.toHaveBeenCalled()
  })

  it('回退目标一定是当前用户可见的 tab，不会二次触发拦截', async () => {
    const { getTabbarRedirectPath } = await import('@/tabbar/store')
    const redirectPath = getTabbarRedirectPath('/pages/point/point')

    expect(redirectPath).toBeTruthy()
    // 对回退目标再判断一次，返回 '' 说明放行，不存在 reLaunch 死循环
    expect(getTabbarRedirectPath(redirectPath)).toBe('')
  })

  it('url 为 undefined 时直接返回，不做任何跳转', async () => {
    await invoke(undefined as unknown as string)

    expect(uni.reLaunch).not.toHaveBeenCalled()
  })
})

describe('navigateToInterceptor 首页就是受限页（issue #454 的核心场景）', () => {
  // 把首页配成 admin 专属，复现「没有 role 却因为它是 home 而直接进入」
  beforeEach(() => {
    vi.doMock('@/tabbar/config', () => ({
      TABBAR_STRATEGY_MAP: { NO_TABBAR: 0, NATIVE_TABBAR: 1, CUSTOM_TABBAR: 2 },
      selectedTabbarStrategy: 2,
      tabbarList: [
        { text: '首页', pagePath: 'pages/member/member', iconType: 'unocss', icon: 'i-carbon-home', roles: ['admin'] },
        { text: '我的', pagePath: 'pages/point/point', iconType: 'unocss', icon: 'i-carbon-user' },
      ],
    }))
  })

  it('冷启动落在受限首页时被拦截，回退到可见的 tab', async () => {
    // App.vue onShow 首次进入时就是这样调用的：没有 options.path 时传 '/'
    const result = await invoke('/')

    expect(result).toBe(false)
    expect(uni.reLaunch).toHaveBeenCalledWith({ url: '/pages/point/point' })
  })

  it('冷启动带上具体首页路径时同样被拦截', async () => {
    const result = await invoke('/pages/member/member')

    expect(result).toBe(false)
    expect(uni.reLaunch).toHaveBeenCalledWith({ url: '/pages/point/point' })
  })

  it('角色满足时冷启动放行', async () => {
    const result = await invoke('/', { roles: ['admin'] })

    expect(result).not.toBe(false)
    expect(uni.reLaunch).not.toHaveBeenCalled()
  })
})
