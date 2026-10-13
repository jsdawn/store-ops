<script lang="ts" setup>
import { bindMobile } from '@/api/mp'
import { useMpStore, useTokenStore } from '@/store'

definePage({
  style: {
    navigationBarTitleText: '完善会员信息',
  },
})

const mpStore = useMpStore()
const tokenStore = useTokenStore()

const nickName = ref('')
const mobile = ref('')
const loading = ref(false)

/**
 * 确认：两字段必填 + 11 位手机号前端拦截（后端二次校验）→ 登录闭环 → 回会员中心
 */
async function handleConfirm() {
  const name = nickName.value.trim()
  const phone = mobile.value.trim()
  if (!name) {
    uni.showToast({ title: '请输入昵称', icon: 'none' })
    return
  }
  if (!/^1\d{10}$/.test(phone)) {
    uni.showToast({ title: '手机号格式不正确', icon: 'none' })
    return
  }
  if (!mpStore.storeId || !mpStore.openId) {
    uni.showToast({ title: '登录态缺失，请重新扫码进入', icon: 'none' })
    uni.redirectTo({ url: '/pages/login/index' })
    return
  }
  if (loading.value)
    return
  loading.value = true
  try {
    const res = await bindMobile({
      storeId: mpStore.storeId,
      openId: mpStore.openId,
      mobile: phone,
      nickName: name,
    })
    tokenStore.setTokenInfo({ token: res.token, expiresIn: res.expiresIn ?? 0 })
    mpStore.setSession(res.member)
    uni.switchTab({ url: '/pages/member/member' })
  }
  catch {
    // HttpError 已由请求层归一处理
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <view class="min-h-screen bg-cream px-6 pt-8">
    <text class="text-sm text-gray-500">首次登录，完善昵称与手机号后即可使用会员服务</text>

    <view class="mt-6 rounded-2xl bg-white p-4">
      <view class="flex items-center border-b border-gray-100 py-4">
        <text class="w-20 shrink-0"><text class="text-red-500">*</text> 昵称</text>
        <wd-input
          v-model="nickName"
          type="nickname"
          placeholder="小王"
          :maxlength="20"
          custom-class="flex-1"
        />
      </view>
      <view class="flex items-center py-4">
        <text class="w-20 shrink-0"><text class="text-red-500">*</text> 手机号</text>
        <wd-input
          v-model="mobile"
          type="number"
          placeholder="13866886688"
          :maxlength="11"
          custom-class="flex-1"
        />
      </view>
    </view>

    <!-- 底部操作栏：确认按钮（通栏） -->
    <view class="fixed inset-x-0 bottom-0 bg-white px-6 pb-8 pt-3">
      <wd-button block custom-class="confirm-btn" :loading="loading" @click="handleConfirm">
        确认
      </wd-button>
    </view>
  </view>
</template>

<style scoped>
.confirm-btn {
  background: #ff7a29 !important;
  border-color: #ff7a29 !important;
  color: #fff !important;
  border-radius: 44rpx;
  height: 88rpx;
}
</style>
