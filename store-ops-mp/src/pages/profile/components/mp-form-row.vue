<script lang="ts" setup>
/**
 * 资料设置页表单行（设计稿 186:42/47）：
 * 左 label 固定宽 128rpx（13px SemiBold）+ 中间输入（slot）+ 右侧「必填」提示 18rpx，
 * 行高由 py 28rpx 撑起，供页面 components 局部使用。
 */
defineProps<{
  label: string
  required?: boolean
}>()
</script>

<template>
  <view class="flex items-center gap-[20rpx] py-[28rpx]">
    <text class="w-[128rpx] shrink-0 text-[26rpx] text-[#2d2a32] font-semibold">{{ label }}</text>
    <view class="min-w-0 flex-1">
      <slot />
    </view>
    <text v-if="required" class="shrink-0 text-[18rpx] text-[#b5b2c2]">必填</text>
  </view>
</template>
