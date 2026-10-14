<script lang="ts" setup>
/**
 * 渐变主按钮（设计稿公共元素）：#FF9A3D → #FF6B5C 左右渐变，
 * 高 96rpx、圆角 48rpx、白字 30rpx SemiBold（登录页/资料设置页共用）。
 * fg- 前缀走 easycom 全局注册，无需手动 import。
 */
interface Props {
  /** 加载态：禁点并展示 spinner */
  loading?: boolean
  disabled?: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{ click: [] }>()

function handleClick() {
  if (props.loading || props.disabled)
    return
  emit('click')
}
</script>

<template>
  <button
    class="fg-gradient-btn center"
    :class="{ 'is-disabled': loading || disabled }"
    :disabled="loading || disabled"
    @click="handleClick"
  >
    <view v-if="loading" class="fg-gradient-btn__spinner" />
    <slot />
  </button>
</template>

<style scoped>
.fg-gradient-btn {
  width: 100%;
  height: 96rpx;
  border-radius: 48rpx;
  padding: 0;
  margin: 0;
  border: none;
  background: linear-gradient(90deg, #ff9a3d 0%, #ff6b5c 100%);
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 1;
  box-shadow: 0 8rpx 24rpx rgba(255, 118, 77, 0.28);
}

/* uni-app button 默认边框/高亮清除 */
.fg-gradient-btn::after {
  border: none;
}

.fg-gradient-btn.is-disabled {
  opacity: 0.72;
}

.fg-gradient-btn__spinner {
  width: 28rpx;
  height: 28rpx;
  margin-right: 12rpx;
  border: 4rpx solid rgba(255, 255, 255, 0.4);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: fg-spin 0.8s linear infinite;
}

@keyframes fg-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
