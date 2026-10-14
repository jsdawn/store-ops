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
  const sid = parseStoreId(query?.scene)
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
    // openId 两个分支都下发：命中档案也必须写回 store，
    // 否则后续编辑资料 preCheck 缺 openId 会误跳登录页
    if (res.openId)
      mpStore.setOpenId(res.openId)
    if (res.token && res.member) {
      // 命中档案：single 模式写 token（拦截器以 Mp-Token 头携带）
      tokenStore.setTokenInfo({ token: res.token, expiresIn: res.expiresIn ?? 0 })
      mpStore.setSession(res.member)
      uni.reLaunch({ url: '/pages/member/member' })
    }
    else if (res.needBindMobile && res.openId) {
      // 首次登录：昵称/手机号为空 → 完善信息
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
  <!-- 设计稿 186:1 会员登录：奶油底 + 双装饰圆 + 品牌区 + 底部渐变登录按钮 -->
  <view class="relative box-border min-h-screen flex flex-col items-center overflow-hidden bg-cream pt-[var(--status-bar-height)]">
    <!-- 背景装饰圆（右上橙 6% / 左下黄 8%，超出裁切） -->
    <view class="pointer-events-none absolute right-[-160rpx] top-[-180rpx] h-520rpx w-520rpx rounded-full bg-[#ff7a29]/6" />
    <view class="pointer-events-none absolute bottom-[-132rpx] left-[-140rpx] h-440rpx w-440rpx rounded-full bg-[#f5a623]/8" />

    <!-- 门店品牌区（门店名后续门店接口就绪后由 mpStore/门店接口回填） -->
    <view class="relative mt-[304rpx] flex flex-col items-center">
      <view class="h-184rpx w-184rpx center rounded-full from-[#ff9a3d] to-[#ff6b5c] bg-gradient-to-r">
        <view class="i-my-icons-scissors text-[76rpx] text-white" />
      </view>
      <text class="mt-[52rpx] text-[40rpx] text-[#2d2a32] font-semibold">才华美甲</text>
      <text class="mt-[10rpx] text-[22rpx] text-[#8a8698]">已通过门店码进入，登录后使用会员服务</text>
    </view>

    <!-- 主操作：微信快捷登录（设计稿按钮 315x48@x30，提示语在按钮下方 32rpx） -->
    <view class="relative mt-auto w-full pb-safe">
      <view class="px-[60rpx] pb-[84rpx]">
        <fg-gradient-button :loading="loading" @click="handleLogin">
          <view class="center">
            <view class="i-my-icons-wechat mr-[12rpx] text-[40rpx]" />
            <text>微信快捷登录</text>
          </view>
        </fg-gradient-button>
        <text class="mt-[32rpx] block text-center text-[20rpx] text-[#b5b2c2]">
          首次登录将引导完善昵称与手机号
        </text>
      </view>
    </view>
  </view>
</template>
