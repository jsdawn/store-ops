<script lang="ts" setup>
/**
 * 会员中心服务行（设计稿 3:1 服务列表卡/工作台卡行元素）：
 * 左 32px 圆角 icon 底 + 标题/副文 + 右箭头，整行可点击。
 */
defineProps<{
  /** my-icons 图标类名，如 i-my-icons-coupon */
  icon: string
  /** icon 底色类，如 bg-[#fff6dc] */
  iconBg: string
  /** icon 线条色（currentColor 渲染），如 #ff7a29 */
  iconColor?: string
  title: string
  subtitle?: string
}>()

const emit = defineEmits<{ click: [] }>()
</script>

<template>
  <view
    class="flex items-center gap-[24rpx] px-[32rpx] py-[24rpx]"
    hover-class="bg-[#faf8fc]"
    :hover-stay-time="60"
    @click="emit('click')"
  >
    <view class="h-[64rpx] w-[64rpx] center shrink-0 rounded-[18rpx]" :class="iconBg">
      <view class="text-[36rpx]" :class="icon" :style="{ color: iconColor }" />
    </view>
    <view class="min-w-0 flex-1">
      <text class="block text-[30rpx] text-[#2d2a32] font-medium">{{ title }}</text>
      <text v-if="subtitle" class="mt-[4rpx] block truncate text-[22rpx] text-[#8a8698]">
        {{ subtitle }}
      </text>
    </view>
    <view class="i-my-icons-chevron-right shrink-0 text-[28rpx] text-[#c6c3d1]" />
  </view>
</template>
