<script lang="ts" setup>
import { getStaffHome } from '@/api/mp'
import { useMpStore } from '@/store'

/**
 * 员工工作台首页（设计稿 39:418「商家工作台」，还原度对齐 375×844 稿 ×2rpx）
 *
 * 数据：/mp/staff/home（token 内 employeeId 非空才可访问，服务层实时复检员工身份，
 * 停用/调岗后旧 token 立即失效 → 401/403 由请求层与本地兜底引导返回会员中心）。
 * 宫格入口：会员充值已上线（/pages/staff/recharge）；收银开单 / 会员查询 / 今日流水 v1 占位建设中。
 */
definePage({
  style: {
    // 沉浸式：渐变 Hero 延伸到状态栏后
    navigationStyle: 'custom',
  },
})

const mpStore = useMpStore()

const storeName = ref('')
const staffName = ref('')

onLoad(() => {
  fetchHome()
})

async function fetchHome() {
  try {
    const res = await getStaffHome()
    storeName.value = res.storeName
    staffName.value = res.staffName
  }
  catch {
    // 非员工 / 员工身份已失效（后端 403）；401 由请求层统一清态跳登录
    uni.showToast({ title: '员工身份已失效', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 600)
  }
}

/** 退出员工视角：返回会员中心（会话保留，仅离开工作台） */
function exitStaff() {
  uni.navigateBack()
}

/** 宫格入口：充值页已上线，其余对应功能页后续迭代 */
function open(item: (typeof gridItems)[number]) {
  if (item.path)
    uni.navigateTo({ url: item.path })
  else
    uni.showToast({ title: '功能建设中', icon: 'none' })
}

/** 宫格配置：icon 色块底色 / icon 色 / 标题 / 副标题 / 路由（设计稿 39:418 宫格卡） */
const gridItems = [
  { icon: 'i-my-icons-cashier', bg: 'bg-[#e8f1ff]', color: '#4d9fff', title: '收银开单', subtitle: '逐笔录入 · 确认收款', path: '' },
  { icon: 'i-my-icons-wallet', bg: 'bg-[#fff6dc]', color: '#f5a623', title: '会员充值', subtitle: '线下收款 · 确认到账', path: '/pages/staff/recharge' },
  { icon: 'i-my-icons-staff-search', bg: 'bg-[#e6f9ee]', color: '#34c77b', title: '会员查询', subtitle: '本店会员池', path: '' },
  { icon: 'i-my-icons-flow', bg: 'bg-[#ffe9f0]', color: '#ff5c7a', title: '今日流水', subtitle: '订单 · 支付方式', path: '' },
] as const
</script>

<template>
  <view class="box-border min-h-screen bg-cream">
    <!-- Hero 渐变头（左上橙 → 右下红 45°，同会员中心）：状态栏沉浸 + 门店身份条 -->
    <view class="rounded-b-[48rpx] from-[#ff9a3d] to-[#ff6b5c] bg-gradient-to-br px-[32rpx] pb-[52rpx] pt-[var(--status-bar-height)]">
      <!-- 导航占位（微信胶囊浮于右上） -->
      <view class="h-[60px]" />

      <!-- 门店身份条：左门店信息两行 / 右退出胶囊（设计稿 pad-top 8 / gap 10） -->
      <view class="flex items-center gap-[20rpx] pt-[16rpx]">
        <view class="min-w-0 flex flex-1 flex-col gap-[8rpx]">
          <text class="truncate text-[30rpx] text-white font-bold">{{ storeName || '…' }}</text>
          <text class="truncate text-[20rpx] text-white/85">
            店员：{{ staffName || '…' }} · 商家工作台
          </text>
        </view>
        <view
          class="h-[52rpx] flex shrink-0 items-center rounded-full bg-white/20 px-[24rpx]"
          hover-class="bg-white/30"
          :hover-stay-time="60"
          @click="exitStaff"
        >
          <text class="text-[22rpx] text-white">退出</text>
        </view>
      </view>
    </view>

    <!-- 页面内容区：pad 14/12/16 gap 12（设计稿 ×2） -->
    <view class="flex flex-col gap-[24rpx] px-[28rpx] pb-[32rpx] pt-[24rpx]">
      <!-- 工作台宫格卡：白底 r16 pad6，2×2 宫格（项 py14 gap7 竖排居中） -->
      <view class="grid grid-cols-2 rounded-[32rpx] bg-white p-[12rpx]">
        <view
          v-for="item in gridItems"
          :key="item.title"
          class="flex flex-col items-center gap-[14rpx] py-[28rpx]"
          hover-class="opacity-80"
          :hover-stay-time="60"
          @click="open(item)"
        >
          <view class="h-[92rpx] w-[92rpx] center rounded-[28rpx]" :class="item.bg">
            <view class="text-[46rpx]" :class="[item.icon, item.color]" />
          </view>
          <text class="text-[26rpx] text-[#2d2a32] font-semibold">{{ item.title }}</text>
          <text class="text-[20rpx] text-[#8a8698]">{{ item.subtitle }}</text>
        </view>
      </view>

      <!-- 管理台职责提示条：#FFF0E4 r12 pad14/10 gap8（设计稿文案原样） -->
      <view class="flex items-center gap-[16rpx] rounded-[24rpx] bg-[#fff0e4] px-[28rpx] py-[20rpx]">
        <view class="i-my-icons-info shrink-0 text-[32rpx] text-[#ff7a29]" />
        <text class="min-w-0 flex-1 text-[20rpx] text-[#c05a17] leading-[1.5]">
          充值冲正、优惠券配置在管理台操作，小程序无冲正入口
        </text>
      </view>
    </view>
  </view>
</template>
