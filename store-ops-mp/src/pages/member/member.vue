<script lang="ts" setup>
import { getMe, getMyStores, switchStore } from '@/api/mp'
import { useMpStore, useTokenStore } from '@/store'
import { parseStoreId } from '@/utils/scene'
import MemberMenuRow from './components/member-menu-row.vue'
import StorePicker from './components/store-picker.vue'

definePage({
  style: {
    // 沉浸式：渐变 Hero 延伸到状态栏后（设计稿 3:1 会员中心）
    navigationStyle: 'custom',
  },
})

const mpStore = useMpStore()
const tokenStore = useTokenStore()

/** 扫码进店的目标门店（onLoad 暂存，onShow 消费一次；切店中防重入） */
const pendingStoreId = ref<string>()
const switchingStore = ref(false)

onLoad((query) => {
  // 扫码进入：scene 携带门店ID（兼容 s=/s_id=/store_id=/纯数字）。
  // 只暂存不写 store：是否切店由 onShow 按会话归属决定，避免 fetchMe 打到错店
  pendingStoreId.value = parseStoreId(query?.scene ?? query?.storeId)
})

onShow(() => {
  const sid = pendingStoreId.value
  pendingStoreId.value = undefined
  // 未扫码且无门店上下文：展示引导文案，不做跳转
  if (!mpStore.storeId && !sid)
    return
  // 未登录（或会话不完整）：带扫码店去登录页，登录后 bind-mobile 落在扫码店
  if (!tokenStore.hasLogin || !mpStore.hasSession) {
    mpStore.setStore(sid || mpStore.storeId)
    uni.redirectTo({ url: '/pages/login/index' })
    return
  }
  // 扫码店非当前会话门店：自动切店（未注册则转登录，见 enterStore）
  if (sid && sid !== mpStore.member?.storeId) {
    enterStore(sid)
    return
  }
  if (sid)
    mpStore.setStore(sid)
  fetchMe()
})

/**
 * 扫码进店：已在目标店注册 → 自动切换（switch-store 重签 token）；
 * 未注册 → 清本地会话重新登录（登录后 bind-mobile 在扫码店建档）。
 * 切店失败（如门店暂停营业）时留在当前店正常展示。
 */
async function enterStore(sid: string) {
  if (switchingStore.value)
    return
  switchingStore.value = true
  uni.showLoading({ title: '正在切换门店', mask: true })
  try {
    const stores = await getMyStores()
    const target = stores.find(s => s.storeId === sid)
    if (target && !target.current) {
      const res = await switchStore(sid)
      tokenStore.setTokenInfo({ token: res.token, expiresIn: res.expiresIn ?? 0 })
      mpStore.setSession(res.member)
      mpStore.setStore(res.member.storeId)
      uni.hideLoading()
      uni.showToast({ title: `已切换到 ${res.member.storeName}`, icon: 'none' })
    }
    else if (target?.current) {
      uni.hideLoading()
      mpStore.setStore(sid)
      fetchMe()
    }
    else {
      uni.hideLoading()
      tokenStore.setTokenInfo({ token: '', expiresIn: 0 })
      mpStore.clearSession()
      mpStore.setStore(sid)
      uni.showToast({ title: '该门店尚未注册会员，请重新登录', icon: 'none' })
      uni.redirectTo({ url: `/pages/login/index?scene=${sid}` })
    }
  }
  catch {
    uni.hideLoading()
    fetchMe()
  }
  finally {
    switchingStore.value = false
  }
}

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

/** 点击会员名称 → 编辑页（页面内自查档案回填，手机号锁定） */
function goEditProfile() {
  uni.navigateTo({ url: '/pages/profile/setup?mode=edit' })
}

/** 切换门店弹层（Hero 切换胶囊 / 服务列表「切换门店」行共用入口） */
const showStorePicker = ref(false)

// ---- 展示态（无接口的数据先静态占位，接口就绪后替换） ----
const member = computed(() => mpStore.member)

/** 门店名：档案回带优先，未接入门店详情接口前兜底静态 */
const storeName = computed(() => member.value?.storeName || '才华美甲 · 门店')
/** 营业态副行：门店详情接口未就绪，静态占位 */
const storeSubtitle = '营业中 · 上次消费 2 小时前'
/** 手机号脱敏：138****6688 */
const maskedMobile = computed(() =>
  member.value?.mobile?.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') || '未绑定手机号',
)
/** 余额（元）：接口已接入，兜底 0.00 */
const balanceText = computed(() => Number(member.value?.balance ?? 0).toFixed(2))
/** 积分：接口已接入，兜底 0 */
const pointText = computed(() => String(member.value?.pointBalance ?? 0))
/** 优惠券数量：优惠券接口未就绪，静态占位 */
const couponCount = '3'
/** 卡号展示 */
const memberNoText = computed(() => member.value?.memberNo || '-')
</script>

<template>
  <!-- 未扫码进入 -->
  <view v-if="!mpStore.storeId" class="min-h-screen bg-cream pt-100 text-center text-gray-500">
    请通过门店小程序码进入本程序
  </view>

  <view v-else class="box-border min-h-screen bg-cream">
    <!-- Hero 渐变卡（设计稿 3:1）：状态栏沉浸 + 门店条 + 会员信息行 -->
    <view class="rounded-b-[48rpx] from-[#ff9a3d] to-[#ff6b5c] bg-gradient-to-r px-[32rpx] pb-[48rpx] pt-[var(--status-bar-height)]">
      <!-- 导航占位（微信胶囊浮于右上，设计稿导航区高 36px） -->
      <view class="h-[60px]" />

      <!-- 门店条 -->
      <view class="flex items-center gap-[18rpx]">
        <view class="i-my-icons-store shrink-0 text-[36rpx] text-white" />
        <view class="min-w-0 flex-1">
          <text class="block truncate text-[30rpx] text-white font-medium">{{ storeName }}</text>
          <text class="mt-[4rpx] block truncate text-[20rpx] text-white/85">{{ storeSubtitle }}</text>
        </view>
        <view
          class="h-[52rpx] flex shrink-0 items-center gap-[6rpx] rounded-full bg-white/20 px-[22rpx]"
          hover-class="bg-white/30"
          :hover-stay-time="60"
          @click="showStorePicker = true"
        >
          <text class="text-[22rpx] text-white">切换</text>
          <view class="i-my-icons-chevron-right text-[20rpx] text-white" />
        </view>
      </view>

      <!-- 会员信息行（点名称进编辑页） -->
      <view class="mt-[20rpx] flex items-center gap-[24rpx]">
        <view class="h-[104rpx] w-[104rpx] center shrink-0 rounded-full bg-white/25">
          <view class="i-my-icons-user text-[64rpx] text-white" />
        </view>
        <view class="min-w-0 flex-1">
          <view
            class="flex items-center gap-[8rpx]"
            hover-class="opacity-80"
            :hover-stay-time="60"
            @click="goEditProfile"
          >
            <text class="text-[38rpx] text-white font-semibold">{{ member?.nickName || '会员' }}</text>
            <view class="i-my-icons-chevron-right text-[28rpx] text-white/70" />
          </view>
          <text class="mt-[8rpx] block truncate text-[22rpx] text-white/85">
            {{ maskedMobile }} · 会员卡号 {{ memberNoText }}
          </text>
        </view>
      </view>
    </view>

    <!-- 页面内容区：左右 28rpx / 卡间距 24rpx（设计稿 gap 12） -->
    <view class="flex flex-col gap-[24rpx] px-[28rpx] pb-[32rpx] pt-[24rpx]">
      <!-- 我的资产卡：三栏（余额/积分接口已接入，优惠券静态） -->
      <view class="flex items-center rounded-[32rpx] bg-white px-[16rpx] py-[30rpx]">
        <view class="min-w-0 flex-1 text-center">
          <view class="text-[40rpx] text-[#2d2a32] font-bold leading-none">
            ¥{{ balanceText }}
          </view>
          <view class="mt-[10rpx] text-[22rpx] text-[#8a8698]">
            余额（元）
          </view>
        </view>
        <view class="h-[68rpx] w-[2rpx] shrink-0 bg-[#f4f2f8]" />
        <view class="min-w-0 flex-1 text-center">
          <view class="text-[40rpx] text-[#2d2a32] font-bold leading-none">
            {{ pointText }}
          </view>
          <view class="mt-[10rpx] text-[22rpx] text-[#8a8698]">
            积分
          </view>
        </view>
        <view class="h-[68rpx] w-[2rpx] shrink-0 bg-[#f4f2f8]" />
        <view class="min-w-0 flex-1 text-center">
          <view class="text-[40rpx] text-[#2d2a32] font-bold leading-none">
            {{ couponCount }} 张
          </view>
          <view class="mt-[10rpx] text-[22rpx] text-[#8a8698]">
            优惠券
          </view>
        </view>
      </view>

      <!-- 充值活动提示条（活动配置接口未就绪，静态占位） -->
      <view class="flex items-center gap-[16rpx] rounded-[24rpx] bg-[#fff0e4] px-[28rpx] py-[20rpx]">
        <view class="i-my-icons-gift shrink-0 text-[32rpx] text-[#c05a17]" />
        <text class="min-w-0 flex-1 text-[22rpx] text-[#c05a17] leading-[1.5]">
          充值活动进行中：充 100 送 10 · 到账 110 元 —— 到店办理，余额即时到账
        </text>
      </view>

      <!-- 服务列表卡：前三行一组 / 分隔线 / 切换门店（记录类接口未就绪，副文静态） -->
      <view class="rounded-[32rpx] bg-white py-[8rpx]">
        <MemberMenuRow
          icon="i-my-icons-coupon"
          icon-bg="bg-[#fff6dc]"
          icon-color="#ff7a29"
          title="我的优惠券"
          subtitle="3 张可使用 · 1 张 10-25 到期"
        />
        <MemberMenuRow
          icon="i-my-icons-consume"
          icon-bg="bg-[#e8f1ff]"
          icon-color="#5a8cff"
          title="消费记录"
          subtitle="累计消费 12 次"
        />
        <MemberMenuRow
          icon="i-my-icons-recharge"
          icon-bg="bg-[#fff6ec]"
          icon-color="#ff7a29"
          title="充值记录"
        />
        <view class="mx-[32rpx] h-[2rpx] bg-[#f4f2f8]" />
        <MemberMenuRow
          icon="i-my-icons-store"
          icon-bg="bg-[#fff6ec]"
          icon-color="#ff7a29"
          title="切换门店"
          :subtitle="`当前：${storeName} · 底部弹出选择`"
          @click="showStorePicker = true"
        />
      </view>

      <!-- 商家工作台卡（员工身份接口未就绪，静态占位） -->
      <view class="rounded-[32rpx] bg-white py-[8rpx]">
        <MemberMenuRow
          icon="i-my-icons-workbench"
          icon-bg="bg-[#fff0e4]"
          icon-color="#ff7a29"
          title="商家工作台"
          subtitle="您已绑定员工身份 · 收银 / 充值 / 会员查询"
        />
      </view>

      <!-- 脚注 -->
      <text class="text-center text-[20rpx] text-[#b5b2c2] leading-[1.6]">
        工作台入口仅限定员工身份的会员可见 · 门店列表仅展示已注册门店
      </text>
    </view>

    <!-- 切换门店底部弹层（选中后重签 token 写回会话，资产区 computed 自动刷新） -->
    <StorePicker v-model="showStorePicker" />
  </view>
</template>
