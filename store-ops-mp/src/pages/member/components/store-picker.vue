<script lang="ts" setup>
import type { IMpStoreBrief } from '@/api/mp'
import { getMyStores, switchStore } from '@/api/mp'
import { useMpStore, useTokenStore } from '@/store'

/**
 * 切换门店底部弹层（设计稿 3:173「底部弹层 · 切换门店」）：
 * 「选中 → 确认」两段式交互，点卡片仅切选中态，点渐变按钮才真正调
 * switchStore 重签 token 并写回会话，父页面 computed 自动响应。
 * 「扫门店码」扫码成功后关闭弹层并 emit('scanned', 原始内容)，
 * 由父页面统一走 enterStore（已注册自动切换 / 未注册转登录）。
 */
const emit = defineEmits<{ scanned: [raw: string] }>()

const visible = defineModel<boolean>({ default: false })

const mpStore = useMpStore()
const tokenStore = useTokenStore()

const stores = ref<IMpStoreBrief[]>([])
const loading = ref(false)
/** 待确认切换的门店ID，打开弹层时默认选中当前店 */
const pendingId = ref('')
/** 确认切换进行中（防重） */
const switching = ref(false)

const pendingStore = computed(() => stores.value.find(s => s.storeId === pendingId.value))
/** 选中的是当前店（或仍在加载）时确认按钮禁用 */
const canConfirm = computed(() => !!pendingStore.value && !pendingStore.value.current && !switching.value)

watch(visible, (v) => {
  if (v)
    fetchStores()
})

async function fetchStores() {
  loading.value = true
  pendingId.value = ''
  try {
    stores.value = await getMyStores()
    pendingId.value = stores.value.find(s => s.current)?.storeId ?? ''
  }
  catch {
    // 业务错误已由请求层 toast
  }
  finally {
    loading.value = false
  }
}

function assetLine(s: IMpStoreBrief) {
  return `卡号 ${s.memberNo} · 余额 ¥${Number(s.balance ?? 0).toFixed(2)} · 积分 ${s.pointBalance ?? 0}`
}

async function handleConfirm() {
  const target = pendingStore.value
  if (!target || target.current || switching.value)
    return
  switching.value = true
  try {
    const res = await switchStore(target.storeId)
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
    switching.value = false
  }
}

function handleScan() {
  uni.scanCode({
    onlyFromCamera: true,
    success: ({ result }) => {
      visible.value = false
      emit('scanned', result ?? '')
    },
  })
}
</script>

<template>
  <wd-popup
    v-model="visible"
    position="bottom"
    lock-scroll
    custom-style="background: #ffffff; border-radius: 40rpx 40rpx 0 0; box-shadow: 0 -6px 24px rgba(46, 41, 51, 0.12);"
    :safe-area-inset-bottom="true"
  >
    <view class="flex flex-col gap-[24rpx] px-[40rpx] pb-[12rpx] pt-[16rpx]">
      <!-- 拖拽指示条 -->
      <view class="mx-auto h-[8rpx] w-[72rpx] rounded-[4rpx] bg-[#e5e2ec]" />

      <!-- 标题行：左标题 + 右上圆形关闭钮 -->
      <view class="flex items-center justify-between pt-[20rpx]">
        <text class="text-[34rpx] text-[#2d2a32] font-bold">切换门店</text>
        <view
          class="h-[56rpx] w-[56rpx] center rounded-[28rpx] bg-[#f4f2f8]"
          hover-class="bg-[#eceaf2]"
          :hover-stay-time="60"
          @click="visible = false"
        >
          <view class="i-my-icons-close text-[24rpx] text-[#8a8698]" />
        </view>
      </view>

      <text class="text-[22rpx] text-[#8a8698]">仅显示已注册会员的门店 · 各店余额 / 积分 / 优惠券独立，不互通</text>

      <!-- 门店卡片列表：最高 60vh 内滚动 -->
      <view class="max-h-[60vh] flex flex-col gap-[24rpx] overflow-y-auto">
        <!-- 加载骨架 -->
        <template v-if="loading">
          <view v-for="i in 3" :key="i" class="h-[120rpx] animate-pulse rounded-[28rpx] bg-[#faf8fb]" />
        </template>

        <!-- 空态 -->
        <view v-else-if="!stores.length" class="py-[80rpx] text-center text-[24rpx] text-[#8a8698]">
          暂无已注册门店
        </view>

        <!-- 门店卡：当前店奶油底橙描边 + 「当前」徽章；点卡片仅改选中态 -->
        <view
          v-for="s in stores"
          :key="s.storeId"
          class="flex items-center gap-[24rpx] border-[1.5px] rounded-[28rpx] px-[28rpx] py-[26rpx]"
          :class="pendingId === s.storeId ? 'border-[#ff7a29] bg-[#fff8f1]' : 'border-[#f0edf5] bg-white'"
          :hover-class="pendingId === s.storeId ? '' : 'bg-[#faf8fb]'"
          :hover-stay-time="60"
          @click="pendingId = s.storeId"
        >
          <view class="min-w-0 flex flex-1 flex-col gap-[10rpx]">
            <view class="flex items-center gap-[16rpx]">
              <text class="truncate text-[28rpx] text-[#2d2a32]" :class="pendingId === s.storeId ? 'font-semibold' : 'font-medium'">
                {{ s.storeName }}
              </text>
              <view v-if="s.current" class="shrink-0 rounded-[16rpx] bg-[#ff7a29] px-[14rpx] text-[18rpx] text-white leading-[32rpx]">
                当前
              </view>
            </view>
            <text class="truncate text-[22rpx] text-[#8a8698]">{{ assetLine(s) }}</text>
          </view>

          <!-- radio：选中橙底白勾 / 未选白底灰描边；切换中骨架闪烁 -->
          <view
            class="h-[40rpx] w-[40rpx] shrink-0 border rounded-[20rpx]"
            :class="[
              pendingId === s.storeId ? 'border-[#ff7a29] bg-[#ff7a29] center' : 'border-[1.5px] border-[#c9c6d4] bg-white',
              switching && pendingId === s.storeId ? 'animate-pulse' : '',
            ]"
          >
            <view v-if="pendingId === s.storeId" class="i-my-icons-check text-[24rpx] text-white" />
          </view>
        </view>
      </view>

      <!-- 扫门店码入口 -->
      <view
        class="h-[88rpx] flex items-center justify-center gap-[12rpx] border-[1.2px] border-[#ffcfa8] rounded-[28rpx] bg-[#fff6ec]"
        hover-class="bg-[#fff1e2]"
        :hover-stay-time="60"
        @click="handleScan"
      >
        <view class="i-my-icons-scan text-[32rpx] text-[#ff7a29]" />
        <text class="text-[24rpx] text-[#ff7a29] font-medium">扫门店码进入新门店</text>
      </view>

      <!-- 确认切换：选中当前店时禁用（降饱和灰渐变） -->
      <view
        class="h-[92rpx] w-full center rounded-[46rpx] text-[30rpx] text-white font-semibold"
        :class="canConfirm
          ? 'bg-gradient-to-r from-[#ff9a3d] to-[#ff6b5c] shadow-[0_16rpx_40rpx_-12rpx_rgba(255,128,64,0.3)]'
          : 'bg-gradient-to-r from-[#f5c9a6] to-[#f0b3a8]'"
        :hover-class="canConfirm ? 'opacity-90' : ''"
        :hover-stay-time="60"
        @click="handleConfirm"
      >
        {{ switching ? '切换中…' : '确认切换' }}
      </view>
    </view>
  </wd-popup>
</template>
