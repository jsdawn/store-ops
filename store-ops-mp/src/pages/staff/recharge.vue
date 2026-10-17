<script lang="ts" setup>
import type { IMpStaffMemberBrief } from '@/api/mp'
import type { PayTypeOption } from '@/components/business/pay-type-picker.vue'
import { getRechargeToken, staffRecharge } from '@/api/mp'
import MemberPicker from '@/components/business/member-picker.vue'
import PayTypePicker from '@/components/business/pay-type-picker.vue'

/**
 * 会员充值（设计稿 107:107「16 会员充值」，375×844 稿 ×2rpx）：
 * 选会员（弹层搜索回填）→ 本金 + 赠送 → 充值方式（弹层点选回填）→
 * 到账合计/充后余额实时计算 → 确认到账（一次性提交凭证防重复提交，详设 5.2）。
 * 充值即时生效不可撤销，错账由管理台当日冲正（PRD 5.1.2）。
 */
definePage({
  style: {
    navigationBarTitleText: '会员充值',
    navigationBarBackgroundColor: '#ffffff',
    navigationBarTextStyle: 'black',
  },
})

// ---- 表单状态 ----
const selectedMember = ref<IMpStaffMemberBrief>()
const amountText = ref('')
const giftText = ref('')
const payType = ref('1')
const remark = ref('')
const showMemberPicker = ref(false)
const showPayTypePicker = ref(false)
const submitting = ref(false)

/** 充值提交凭证（进入页面与每次成功提交后重新签发，一次有效） */
let rechargeToken = ''

onLoad(() => {
  fetchToken()
})

async function fetchToken() {
  try {
    rechargeToken = await getRechargeToken()
  }
  catch {
    // 失败不阻断浏览，提交时再取
  }
}

const PAY_TYPE_LABELS: Record<string, string> = {
  1: '微信收款',
  2: '支付宝收款',
  3: '现金收款',
}
const payTypeLabel = computed(() => PAY_TYPE_LABELS[payType.value] ?? '')

/** 充值方式选项（member 域字典：1微信 2支付宝 3现金；与 order 域 1余额2微信3支付宝4现金 不同） */
const payTypeOptions: PayTypeOption[] = [
  { value: '1', label: '微信收款', char: '微', bg: '#e6f9ee', color: '#34c77b' },
  { value: '2', label: '支付宝收款', char: '支', bg: '#e8f1ff', color: '#4d9fff' },
  { value: '3', label: '现金收款', char: '现', bg: '#fff6dc', color: '#f5a623' },
]

// ---- 金额与合计 ----
const amountNum = computed(() => Number.parseFloat(amountText.value) || 0)
const giftNum = computed(() => Number.parseFloat(giftText.value) || 0)
const totalAmount = computed(() => amountNum.value + giftNum.value)
/** 充后余额 = 当前余额 + 本金 + 赠送（已选会员时展示） */
const afterBalance = computed(() =>
  Number(selectedMember.value?.balance ?? 0) + totalAmount.value,
)

/** 提交前校验：金额约束（详设 5.2 校验链第 3 步前端部分） */
const formError = computed(() => {
  if (!selectedMember.value)
    return '请先选择会员'
  if (!amountText.value || amountNum.value <= 0)
    return '请输入充值金额'
  if (amountNum.value > 99999.99)
    return '充值金额不能超过 99999.99'
  if (giftNum.value > amountNum.value)
    return '赠送金额不能超过充值本金'
  return ''
})

const canSubmit = computed(() => !formError.value && !submitting.value)

function onMemberPicked(m: IMpStaffMemberBrief) {
  selectedMember.value = m
}

function onPayTypePicked(v: string) {
  payType.value = v
}

async function handleSubmit() {
  const err = formError.value
  if (err) {
    uni.showToast({ title: err, icon: 'none' })
    return
  }
  if (!rechargeToken)
    await fetchToken()
  if (!rechargeToken) {
    uni.showToast({ title: '提交凭证获取失败，请重试', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await staffRecharge({
      memberId: selectedMember.value!.memberId,
      amount: amountNum.value,
      giftAmount: giftNum.value,
      payType: payType.value,
      remark: remark.value.trim() || undefined,
      rechargeToken,
    })
    // 本地即时更新余额展示 + 重置金额，凭证换新供下一笔
    selectedMember.value = {
      ...selectedMember.value!,
      balance: Number(selectedMember.value!.balance ?? 0) + totalAmount.value,
    }
    uni.showToast({ title: `已到账 ¥${totalAmount.value.toFixed(2)}`, icon: 'none' })
    amountText.value = ''
    giftText.value = ''
    remark.value = ''
    fetchToken()
  }
  catch {
    // 失败（含凭证失效）后换新凭证，用户修正后可直接重提
    fetchToken()
  }
  finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="box-border min-h-screen bg-cream">
    <!-- 页面内容区（设计稿 107:118：pad 28rpx，卡片间 16rpx）；导航栏为小程序原生导航 -->
    <view class="flex flex-col gap-[16rpx] px-[28rpx] pb-[240rpx] pt-[20rpx]">
      <!-- 卡1 会员 -->
      <view class="flex flex-col gap-[14rpx] rounded-[32rpx] bg-white px-[24rpx] py-[20rpx] shadow-[0_12rpx_32rpx_-8rpx_rgba(255,140,64,0.08)]">
        <view class="flex items-baseline gap-[6rpx]">
          <text class="text-[26rpx] text-[#2d2a32] font-semibold">会员</text>
          <text class="text-[26rpx] text-[#ff7a29]">*</text>
          <text class="ml-[12rpx] text-[18rpx] text-[#b5b2c2]">点击选择，弹窗搜索会员</text>
        </view>

        <!-- 选择行：未选灰态 / 已选奶油底橙描边（设计稿 107:124） -->
        <view
          class="flex items-center gap-[20rpx] rounded-[24rpx] px-[24rpx] py-[20rpx]"
          :class="selectedMember ? 'border-[1.5px] border-[#ff7a29] bg-[#fff8f1]' : 'border border-[#f0edf5] bg-[#f5f2f8]'"
          hover-class="opacity-90"
          :hover-stay-time="60"
          @click="showMemberPicker = true"
        >
          <template v-if="selectedMember">
            <view class="h-[68rpx] w-[68rpx] center shrink-0 rounded-full bg-[#ffe3d1] text-[26rpx] text-[#ff7a29] font-semibold">
              {{ (selectedMember.nickName || selectedMember.mobile || '客')[0] }}
            </view>
            <view class="min-w-0 flex flex-1 flex-col gap-[8rpx]">
              <text class="truncate text-[24rpx] text-[#2d2a32] font-medium">
                {{ selectedMember.nickName || '未设置昵称' }} {{ selectedMember.mobile?.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') || '' }}
              </text>
              <text class="text-[18rpx] text-[#8a8698]">当前余额 ¥{{ Number(selectedMember.balance ?? 0).toFixed(2) }}</text>
            </view>
            <text class="shrink-0 text-[20rpx] text-[#ff7a29]">已选</text>
          </template>
          <template v-else>
            <view class="h-[68rpx] w-[68rpx] center shrink-0 rounded-full bg-[#e9e6ef]">
              <view class="i-my-icons-search text-[28rpx] text-[#b5b2c2]" />
            </view>
            <text class="min-w-0 flex-1 text-[22rpx] text-[#b5b2c2]">点击选择，弹窗搜索会员</text>
          </template>
          <view class="i-my-icons-chevron-right shrink-0 text-[20rpx] text-[#b5b2c2]" />
        </view>
      </view>

      <!-- 卡2 充值金额（本金） -->
      <view class="flex flex-col gap-[14rpx] rounded-[32rpx] bg-white px-[24rpx] py-[20rpx] shadow-[0_12rpx_32rpx_-8rpx_rgba(255,140,64,0.08)]">
        <view class="flex items-baseline gap-[6rpx]">
          <text class="text-[26rpx] text-[#2d2a32] font-semibold">充值金额（本金）</text>
          <text class="text-[26rpx] text-[#ff7a29]">*</text>
        </view>
        <view class="h-[92rpx] flex items-center gap-[16rpx] rounded-[24rpx] bg-[#f5f2f8] px-[28rpx]">
          <text class="text-[32rpx] text-[#ff7a29] font-semibold">¥</text>
          <input
            v-model="amountText"
            class="h-full min-w-0 flex-1 text-[44rpx] text-[#2d2a32] font-semibold"
            type="digit"
            placeholder="0.00"
            placeholder-class="text-[#d8d5e0]"
          >
          <text class="text-[18rpx] text-[#b5b2c2]">本金</text>
        </view>
        <text class="text-[18rpx] text-[#b5b2c2]">大于 0，两位小数，上限 99999.99</text>
      </view>

      <!-- 卡3 赠送金额 -->
      <view class="flex flex-col gap-[14rpx] rounded-[32rpx] bg-white px-[24rpx] py-[20rpx] shadow-[0_12rpx_32rpx_-8rpx_rgba(255,140,64,0.08)]">
        <view class="flex items-baseline gap-[12rpx]">
          <text class="text-[26rpx] text-[#2d2a32] font-semibold">赠送金额</text>
          <text class="text-[18rpx] text-[#b5b2c2]">无活动不填 · 不可超过本金</text>
        </view>
        <view class="h-[92rpx] flex items-center gap-[16rpx] rounded-[24rpx] bg-[#f5f2f8] px-[28rpx]">
          <text class="text-[32rpx] text-[#f5a623] font-semibold">¥</text>
          <input
            v-model="giftText"
            class="h-full min-w-0 flex-1 text-[44rpx] text-[#2d2a32] font-semibold"
            type="digit"
            placeholder="0.00"
            placeholder-class="text-[#d8d5e0]"
          >
          <text class="text-[18rpx] text-[#b5b2c2]">赠送</text>
        </view>
      </view>

      <!-- 卡4 充值方式 -->
      <view class="flex flex-col gap-[14rpx] rounded-[32rpx] bg-white px-[24rpx] py-[20rpx] shadow-[0_12rpx_32rpx_-8rpx_rgba(255,140,64,0.08)]">
        <view class="flex items-baseline gap-[6rpx]">
          <text class="text-[26rpx] text-[#2d2a32] font-semibold">充值方式</text>
          <text class="text-[26rpx] text-[#ff7a29]">*</text>
          <text class="ml-[12rpx] text-[18rpx] text-[#b5b2c2]">线下收款标记，不触发线上支付</text>
        </view>
        <view
          class="flex items-center gap-[20rpx] border-[1.5px] border-[#ff7a29] rounded-[24rpx] bg-[#fff8f1] px-[24rpx] py-[20rpx]"
          hover-class="opacity-90"
          :hover-stay-time="60"
          @click="showPayTypePicker = true"
        >
          <view
            class="h-[68rpx] w-[68rpx] center shrink-0 rounded-full text-[26rpx] font-semibold"
            :class="payType === '1' ? 'bg-[#e6f9ee] text-[#34c77b]' : payType === '2' ? 'bg-[#e8f1ff] text-[#4d9fff]' : 'bg-[#fff6dc] text-[#f5a623]'"
          >
            {{ payType === '1' ? '微' : payType === '2' ? '支' : '现' }}
          </view>
          <view class="min-w-0 flex flex-1 flex-col gap-[8rpx]">
            <text class="text-[24rpx] text-[#2d2a32] font-medium">{{ payTypeLabel }}</text>
            <text class="text-[18rpx] text-[#8a8698]">点击切换收款渠道</text>
          </view>
          <text class="shrink-0 text-[20rpx] text-[#ff7a29]">已选</text>
          <view class="i-my-icons-chevron-right shrink-0 text-[20rpx] text-[#b5b2c2]" />
        </view>
      </view>

      <!-- 到账合计块（设计稿 107:163） -->
      <view class="flex flex-col gap-[10rpx] rounded-[32rpx] bg-[#fff8f1] px-[24rpx] py-[20rpx] shadow-[0_12rpx_32rpx_-8rpx_rgba(255,140,64,0.12)]">
        <view class="flex items-center justify-between">
          <text class="text-[22rpx] text-[#8a8698]">到账合计（本金 + 赠送）</text>
          <text class="text-[40rpx] text-[#ff6b2c] font-bold">¥{{ totalAmount.toFixed(2) }}</text>
        </view>
        <view v-if="selectedMember" class="flex items-center justify-between">
          <text class="text-[18rpx] text-[#8a8698]">充后余额</text>
          <text class="text-[22rpx] text-[#8a8698]">¥{{ afterBalance.toFixed(2) }}</text>
        </view>
      </view>

      <!-- 卡5 备注 -->
      <view class="flex flex-col gap-[14rpx] rounded-[32rpx] bg-white px-[24rpx] py-[20rpx] shadow-[0_12rpx_32rpx_-8rpx_rgba(255,140,64,0.08)]">
        <view class="flex items-baseline gap-[12rpx]">
          <text class="text-[26rpx] text-[#2d2a32] font-semibold">备注</text>
          <text class="text-[18rpx] text-[#b5b2c2]">选填</text>
        </view>
        <input
          v-model="remark"
          class="h-[80rpx] rounded-[24rpx] bg-[#f5f2f8] px-[28rpx] text-[22rpx] text-[#2d2a32]"
          placeholder="如：周年庆充值活动"
          placeholder-class="text-[#b5b2c2]"
          :maxlength="50"
        >
      </view>
    </view>

    <!-- 底部操作栏（设计稿 107:176）：白底 + 渐变确认按钮 -->
    <view class="fixed inset-x-0 bottom-0 bg-white pb-[env(safe-area-inset-bottom)] pt-[16rpx]">
      <view class="px-[28rpx]">
        <view
          class="h-[88rpx] w-full center rounded-[44rpx] text-[30rpx] text-white font-semibold"
          :class="canSubmit
            ? 'bg-gradient-to-r from-[#ff9a3d] to-[#ff6b5c] shadow-[0_16rpx_40rpx_-12rpx_rgba(255,128,64,0.3)]'
            : 'bg-gradient-to-r from-[#f5c9a6] to-[#f0b3a8]'"
          :hover-class="canSubmit ? 'opacity-90' : ''"
          :hover-stay-time="60"
          @click="handleSubmit"
        >
          {{ submitting ? '到账中…' : `确认到账 ¥${totalAmount.toFixed(2)}` }}
        </view>
      </view>
    </view>

    <!-- 弹层：选会员 / 选充值方式 -->
    <MemberPicker v-model="showMemberPicker" :selected-id="selectedMember?.memberId" @picked="onMemberPicked" />
    <PayTypePicker
      v-model="showPayTypePicker"
      title="选择充值方式"
      subtitle="点击即选用并回填"
      :options="payTypeOptions"
      :current="payType"
      @picked="onPayTypePicked"
    />
  </view>
</template>
