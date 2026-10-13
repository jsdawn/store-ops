<script lang="ts" setup>
import { getWxCode } from '@/api/login'
import { mpLogin } from '@/api/mp'
import { useMpStore, useTokenStore } from '@/store'
import { parseStoreId } from '@/utils/scene'

definePage({
  style: {
    // 沉浸式：无返回入口（设计稿 20 会员登录，登录前不提供返回）
    navigationStyle: 'custom',
  },
})

const mpStore = useMpStore()
const tokenStore = useTokenStore()
const loading = ref(false)

onLoad((query) => {
  const sid = parseStoreId(query?.scene ?? query?.storeId)
  if (sid)
    mpStore.setStore(sid)
})

/**
 * 微信快捷登录：wx.login 静默取 code → /auth/mp/login
 * 命中档案 → 写会话回会员中心；未命中 → 补昵称/手机号（设计稿 21）
 */
async function handleLogin() {
  if (!mpStore.storeId) {
    uni.showToast({ title: '请从门店小程序码进入', icon: 'none' })
    return
  }
  if (loading.value)
    return
  loading.value = true
  try {
    const { code } = await getWxCode()
    const res = await mpLogin({ code, storeId: mpStore.storeId })
    if (res.token && res.member) {
      // 命中档案：single 模式写 token（拦截器以 Mp-Token 头携带）
      tokenStore.setTokenInfo({ token: res.token, expiresIn: res.expiresIn ?? 0 })
      mpStore.setSession(res.member)
      uni.reLaunch({ url: '/pages/member/member' })
    }
    else if (res.needBindMobile && res.openId) {
      // 首次登录：昵称/手机号为空 → 完善信息
      mpStore.setOpenId(res.openId)
      uni.navigateTo({ url: '/pages/profile/setup' })
    }
    else {
      uni.showToast({ title: '登录响应异常，请重试', icon: 'none' })
    }
  }
  catch {
    // HttpError 已由请求层归一处理（toast / 跳登录）
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <view class="login-page min-h-screen flex flex-col items-center bg-cream">
    <!-- 门店品牌区（门店头像/店名后端就绪后由 mpStore.member 或门店接口回填） -->
    <view class="mt-40 flex flex-col items-center">
      <view class="h-20 w-20 flex items-center justify-center rounded-2xl bg-primary">
        <text class="text-3xl text-white">✂</text>
      </view>
      <text class="mt-4 text-xl font-bold">才华美甲</text>
      <text class="mt-1 text-sm text-gray-500">欢迎光临，扫码即享会员服务</text>
    </view>

    <!-- 主操作：微信快捷登录 -->
    <view class="mt-auto w-full px-8 pb-24">
      <wd-button block custom-class="login-btn" :loading="loading" @click="handleLogin">
        微信快捷登录
      </wd-button>
      <text class="mt-4 block text-center text-xs text-gray-400">
        登录代表您已同意《会员服务协议》
      </text>
    </view>
  </view>
</template>

<style scoped>
.login-page {
  background: #fff6ec;
}

.login-btn {
  background: #ff7a29 !important;
  border-color: #ff7a29 !important;
  color: #fff !important;
  border-radius: 44rpx;
  height: 88rpx;
}
</style>
