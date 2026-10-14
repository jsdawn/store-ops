<script lang="ts" setup>
import type { IMpStoreBrief } from '@/api/mp'
import { getMyStores, switchStore } from '@/api/mp'
import { useMpStore, useTokenStore } from '@/store'

/**
 * 切换门店底部弹层（设计稿 3:1「切换门店」+ 12 选择会员·底部弹层交互约定：
 * 选择器统一底部弹层带回填）。数据由组件内拉取（getMyStores，token 即凭据），
 * 选中调 switchStore 重签 token 并写回会话，父页面 computed 自动响应。
 */
const visible = defineModel<boolean>({ default: false })

const mpStore = useMpStore()
const tokenStore = useTokenStore()

const stores = ref<IMpStoreBrief[]>([])
const loading = ref(false)
/** 切换中的门店ID（防重复点击） */
const switchingId = ref('')

watch(visible, (v) => {
  if (v)
    fetchStores()
})

async function fetchStores() {
  loading.value = true
  try {
    stores.value = await getMyStores()
  }
  catch {
    // 业务错误已由请求层 toast
  }
  finally {
    loading.value = false
  }
}

async function handleSelect(s: IMpStoreBrief) {
  if (s.current || switchingId.value)
    return
  switchingId.value = s.storeId
  try {
    const res = await switchStore(s.storeId)
    tokenStore.setTokenInfo({ token: res.token, expiresIn: res.expiresIn ?? 0 })
    mpStore.setSession(res.member)
    mpStore.setStore(res.member.storeId)
    uni.showToast({ title: `已切换到 ${res.member.storeName}`, icon: 'none' })
    visible.value = false
  }
  catch {
    // 业务错误已由请求层 toast
  }
  finally {
    switchingId.value = ''
  }
}
</script>

<template>
  <wd-popup
    v-model="visible"
    position="bottom"
    round
    closable
    lock-scroll
    custom-style="background: #fff6ec;"
    :safe-area-inset-bottom="true"
  >
    <view class="px-[32rpx] pb-[24rpx] pt-[48rpx]">
      <text class="block text-center text-[32rpx] text-[#2d2a32] font-semibold">切换门店</text>
      <text class="mt-[8rpx] block text-center text-[22rpx] text-[#8a8698]">仅展示已注册会员的门店</text>

      <!-- 列表：最高 60vh 内滚动 -->
      <view class="mt-[28rpx] max-h-[60vh] flex flex-col gap-[20rpx] overflow-y-auto pb-[16rpx]">
        <!-- 加载骨架 -->
        <template v-if="loading">
          <view v-for="i in 3" :key="i" class="h-[132rpx] animate-pulse rounded-[24rpx] bg-white/70" />
        </template>

        <!-- 空态 -->
        <view v-else-if="!stores.length" class="py-[80rpx] text-center text-[24rpx] text-[#8a8698]">
          暂无已注册门店
        </view>

        <!-- 门店行：店名 + 当前 tag / 卡号 + 资产摘要；当前店禁点 -->
        <view
          v-for="s in stores"
          :key="s.storeId"
          class="flex items-center gap-[20rpx] rounded-[24rpx] px-[28rpx] py-[24rpx]"
          :class="s.current ? 'bg-white ring-2 ring-[#ff7a29] ring-inset' : 'bg-white'"
          :hover-class="s.current ? '' : 'bg-[#fffbf5]'"
          :hover-stay-time="60"
          @click="handleSelect(s)"
        >
          <view class="h-[72rpx] w-[72rpx] center shrink-0 rounded-[18rpx] bg-[#fff6ec]">
            <view class="i-my-icons-store text-[40rpx] text-[#ff7a29]" />
          </view>
          <view class="min-w-0 flex-1">
            <view class="flex items-center gap-[12rpx]">
              <text class="truncate text-[30rpx] text-[#2d2a32] font-medium">{{ s.storeName }}</text>
              <view v-if="s.current" class="shrink-0 rounded-full bg-[#fff1e6] px-[14rpx] py-[2rpx] text-[20rpx] text-[#ff7a29]">
                当前
              </view>
            </view>
            <text class="mt-[6rpx] block truncate text-[22rpx] text-[#8a8698]">
              卡号 {{ s.memberNo }} · 余额 ¥{{ Number(s.balance ?? 0).toFixed(2) }} · 积分 {{ s.pointBalance ?? 0 }}
            </text>
          </view>
          <view
            class="shrink-0 text-[28rpx]"
            :class="switchingId === s.storeId ? 'i-my-icons-user text-[#ff7a29] animate-pulse' : 'i-my-icons-chevron-right text-[#c6c3d1]'"
          />
        </view>
      </view>
    </view>
  </wd-popup>
</template>
