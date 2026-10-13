<script lang="ts" setup>
import { getMe } from '@/api/mp'
import { useMpStore, useTokenStore } from '@/store'
import { parseStoreId } from '@/utils/scene'

definePage({
  style: {
    navigationBarTitleText: '会员中心',
  },
})

const mpStore = useMpStore()
const tokenStore = useTokenStore()

onLoad((query) => {
  // 扫码进入：scene 携带门店ID（兼容 s=/s_id=/store_id=/纯数字）
  const sid = parseStoreId(query?.scene ?? query?.storeId)
  if (sid)
    mpStore.setStore(sid)
})

onShow(() => {
  // 未扫码进入：展示引导文案，不做跳转
  if (!mpStore.storeId)
    return
  // 未登录（或会话不完整）：去登录页（登录前无返回入口）
  if (!tokenStore.hasLogin || !mpStore.hasSession) {
    uni.redirectTo({ url: '/pages/login/index' })
    return
  }
  fetchMe()
})

async function fetchMe() {
  if (!mpStore.storeId)
    return
  try {
    // 401 由请求层统一处理（清态 → 跳登录页）
    mpStore.setSession(await getMe(mpStore.storeId))
  }
  catch {
    // 业务错误已由请求层 toast
  }
}
</script>

<template>
  <view class="min-h-screen bg-cream p-6">
    <!-- 未扫码进入 -->
    <view v-if="!mpStore.storeId" class="mt-40 text-center text-gray-500">
      请通过门店小程序码进入本程序
    </view>

    <!-- 已登录：会员资产占位（余额/积分卡后续按详设迭代） -->
    <view v-else-if="mpStore.member" class="mt-4">
      <view class="rounded-2xl bg-primary p-6 text-white">
        <text class="text-sm opacity-80">{{ mpStore.member.storeName || '会员中心' }}</text>
        <view class="mt-2 text-xl font-bold">
          {{ mpStore.member.nickName || '会员' }}
        </view>
        <text class="mt-1 text-xs opacity-80">卡号 {{ mpStore.member.memberNo || '-' }}</text>
      </view>
      <view class="mt-4 flex gap-4">
        <view class="flex-1 rounded-2xl bg-white p-4 text-center">
          <view class="text-lg text-primary font-bold">
            {{ mpStore.member.pointBalance ?? 0 }}
          </view>
          <text class="text-xs text-gray-500">积分</text>
        </view>
        <view class="flex-1 rounded-2xl bg-white p-4 text-center">
          <view class="text-lg text-primary font-bold">
            {{ mpStore.member.balance ?? 0 }}
          </view>
          <text class="text-xs text-gray-500">余额（元）</text>
        </view>
      </view>
    </view>
  </view>
</template>
