<script lang="ts" setup>
/**
 * 支付方式选项（充值 / 收银两场景共用）
 * desc：行副文案（默认「线下收款标记 · 平台不经手资金」）
 * disabled：置灰不可选（散客无余额 / 余额不足，详设 5.2 / 7.1）
 * disabledTip：置灰原因（toast 提示）
 */
export interface PayTypeOption {
  value: string
  label: string
  char: string
  bg: string
  color: string
  desc?: string
  disabled?: boolean
  disabledTip?: string
}

/** 弹层标题与副文案（充值 / 收银文案不同） */
withDefaults(defineProps<{
  title?: string
  subtitle?: string
  options: PayTypeOption[]
  /** 当前已选方式（弹层内高亮展示，未选时无高亮） */
  current?: string
}>(), {
  title: '选择支付方式',
  subtitle: '微信 / 支付宝 / 现金为线下收款标记，平台不经手资金',
  current: '',
})

/**
 * 选择支付方式底部弹层（充值 107:187 / 收银 39:722 员工端复用版）：
 * 点行即选用并回填，不触发线上支付；置灰项（散客余额收款 / 余额不足）点击给原因提示。
 */
const emit = defineEmits<{ picked: [payType: string] }>()

const visible = defineModel<boolean>({ default: false })

const DEFAULT_DESC = '线下收款标记 · 平台不经手资金'

function pick(o: PayTypeOption) {
  if (o.disabled) {
    uni.showToast({ title: o.disabledTip || '当前不可选', icon: 'none' })
    return
  }
  emit('picked', o.value)
  visible.value = false
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
        <view class="flex flex-col gap-[6rpx]">
          <text class="text-[32rpx] text-[#2d2a32] font-bold">{{ title }}</text>
          <text class="text-[20rpx] text-[#8a8698]">{{ subtitle }}</text>
        </view>
        <view
          class="h-[56rpx] w-[56rpx] center rounded-[28rpx] bg-[#f4f2f8]"
          hover-class="bg-[#eceaf2]"
          :hover-stay-time="60"
          @click="visible = false"
        >
          <view class="i-my-icons-close text-[24rpx] text-[#8a8698]" />
        </view>
      </view>

      <!-- 方式行：点行即选用并回填（设计稿 39:722 / 107:201~224）；当前已选行橙描边高亮 -->
      <view class="flex flex-col gap-[20rpx] pb-[8rpx]">
        <view
          v-for="t in options"
          :key="t.value"
          class="flex items-center gap-[24rpx] border-[1.5px] rounded-[28rpx] px-[28rpx] py-[24rpx]"
          :class="[
            t.value === current ? 'border-[#ff7a29] bg-[#fff8f1]' : 'border-[#f0edf5] bg-white',
            t.disabled ? 'opacity-50' : '',
          ]"
          hover-class="bg-[#faf8fb]"
          :hover-stay-time="60"
          @click="pick(t)"
        >
          <view
            class="h-[76rpx] w-[76rpx] center shrink-0 rounded-full text-[28rpx] font-semibold"
            :style="{ background: t.bg, color: t.color }"
          >
            {{ t.char }}
          </view>

          <view class="min-w-0 flex flex-1 flex-col gap-[8rpx]">
            <text class="text-[26rpx] text-[#2d2a32] font-semibold">{{ t.label }}</text>
            <text class="text-[20rpx] text-[#8a8698]">{{ t.desc ?? DEFAULT_DESC }}</text>
          </view>

          <!-- radio：已选橙底白勾 / 未选灰描边圆环（与切店弹层同款） -->
          <view
            class="h-[36rpx] w-[36rpx] shrink-0 rounded-full"
            :class="t.value === current ? 'center border-[1.5px] border-[#ff7a29] bg-[#ff7a29]' : 'border-[1.5px] border-[#c9c6d4] bg-white'"
          >
            <view v-if="t.value === current" class="i-my-icons-check text-[20rpx] text-white" />
          </view>
        </view>
      </view>
    </view>
  </wd-popup>
</template>
