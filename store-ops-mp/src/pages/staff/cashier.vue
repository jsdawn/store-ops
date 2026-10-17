<script lang="ts" setup>
import type { IMpStaffMemberBrief, IMpStaffOrderItem } from '@/api/mp'
import type { PayTypeOption } from '@/components/business/pay-type-picker.vue'
import { getOrderToken, staffCreateOrder, staffPayOrder } from '@/api/mp'
import MemberPicker from '@/components/business/member-picker.vue'
import PayTypePicker from '@/components/business/pay-type-picker.vue'

/**
 * 收银开单（设计稿 39:497「11 收银开单」+ 107:1「添加一笔」，375×844 稿 ×2rpx）：
 * 选会员（可选，弹层搜索回填）→ 逐笔录入明细（添加一笔就地展开）→ 自动合计 →
 * 选支付方式（弹层点选回填）→ 确认收款（开单+收款连续完成，详设 7.1 交互口径）。
 *
 * 两段式（详设 5.1/5.2）：先开单（→待收款）拿订单ID，再立即收款（→已完成）；
 * 中断/收款失败留下待收款单，工作台今日流水可见（本期页面后置）。
 * 优惠券本期 stub（券域 P3），卡3 显示「上线后开放」。
 */
definePage({
  style: {
    navigationBarTitleText: '收银开单',
    navigationBarBackgroundColor: '#ffffff',
    navigationBarTextStyle: 'black',
  },
})

// ---- 表单状态 ----
const selectedMember = ref<IMpStaffMemberBrief>()
const items = ref<IMpStaffOrderItem[]>([])
const payType = ref('2') // 默认微信收款（order 域字典：1余额 2微信 3支付宝 4现金）
const showMemberPicker = ref(false)
const showPayTypePicker = ref(false)
const submitting = ref(false)

/** 添加一笔行内表单（设计稿 107:1：就地展开，确认后追加，取消收起不保留） */
const adding = ref(false)
const draftName = ref('')
const draftPrice = ref('')
const draftQty = ref(1)

/** 开单提交凭证（进入页面重新签发，一次有效，防弱网重复开单） */
let orderToken = ''

onLoad(() => {
  fetchToken()
})

async function fetchToken() {
  try {
    orderToken = await getOrderToken()
  }
  catch {
    // 失败不阻断浏览，提交时再取
  }
}

// ---- 金额与合计 ----
const totalAmount = computed(() =>
  items.value.reduce((sum, it) => sum + it.unitPrice * it.quantity, 0),
)
const memberBalance = computed(() => Number(selectedMember.value?.balance ?? 0))
/** 本笔小计预览（行内表单） */
const draftSubtotal = computed(() => (Number.parseFloat(draftPrice.value) || 0) * draftQty.value)

const formError = computed(() => {
  if (!items.value.length)
    return '请先添加消费明细'
  if (totalAmount.value <= 0)
    return '订单金额必须大于0'
  return ''
})
const canSubmit = computed(() => !formError.value && !submitting.value)

function onMemberPicked(m: IMpStaffMemberBrief) {
  selectedMember.value = m
  // 会员变化后余额收款可用性随之变化；若当前选了余额但已不可用，回退微信
  if (payType.value === '1' && (memberBalance.value < totalAmount.value))
    payType.value = '2'
}

function onPayTypePicked(v: string) {
  payType.value = v
}

// ---- 添加一笔（行内表单） ----
function openAdding() {
  draftName.value = ''
  draftPrice.value = ''
  draftQty.value = 1
  adding.value = true
}

function cancelAdding() {
  adding.value = false
}

function confirmAdding() {
  const price = Number.parseFloat(draftPrice.value)
  if (!price || price <= 0) {
    uni.showToast({ title: '请输入有效单价', icon: 'none' })
    return
  }
  items.value.push({
    itemName: draftName.value.trim() || undefined,
    unitPrice: price,
    quantity: draftQty.value,
  })
  adding.value = false
}

function stepQty(delta: number) {
  const next = draftQty.value + delta
  if (next < 1 || next > 999)
    return
  draftQty.value = next
}

// ---- 支付方式（order 域字典：1余额 2微信 3支付宝 4现金） ----
/** 卡4 选择行图标配置（与弹层行同源） */
const payTypeMeta: Record<string, { label: string, char: string, bg: string, color: string, desc: string }> = {
  1: { label: '余额收款', char: '¥', bg: '#e8f1ff', color: '#4d9fff', desc: '扣减会员余额，同事务失败整单失败' },
  2: { label: '微信收款', char: '微', bg: '#e6f9ee', color: '#34c77b', desc: '线下收款标记 · 平台不经手资金' },
  3: { label: '支付宝收款', char: '支', bg: '#e8f1ff', color: '#4d9fff', desc: '线下收款标记 · 平台不经手资金' },
  4: { label: '现金收款', char: '¥', bg: '#fff6dc', color: '#f5a623', desc: '线下收款标记 · 平台不经手资金' },
}

/** 余额收款置灰：散客不可余额收款；余额不足置灰（服务端最终校验，详设 7.1） */
const balanceDisabled = computed(() => {
  if (!selectedMember.value)
    return { disabled: true, tip: '散客单不支持余额收款' }
  if (memberBalance.value < totalAmount.value)
    return { disabled: true, tip: '会员余额不足' }
  return { disabled: false, tip: '' }
})

const payTypeOptions = computed<PayTypeOption[]>(() => [
  {
    value: '1',
    label: '余额收款',
    char: '¥',
    bg: '#e8f1ff',
    color: '#4d9fff',
    desc: selectedMember.value
      ? `扣减会员余额 ¥${memberBalance.value.toFixed(2)}（同事务，失败整单失败）`
      : '散客单不支持余额收款',
    disabled: balanceDisabled.value.disabled,
    disabledTip: balanceDisabled.value.tip,
  },
  { value: '2', label: '微信收款', char: '微', bg: '#e6f9ee', color: '#34c77b' },
  { value: '3', label: '支付宝收款', char: '支', bg: '#e8f1ff', color: '#4d9fff' },
  { value: '4', label: '现金收款', char: '¥', bg: '#fff6dc', color: '#f5a623' },
])

const currentPayMeta = computed(() => payTypeMeta[payType.value] ?? payTypeMeta[2])

// ---- 提交：开单 → 收款连续完成（详设 7.1） ----
async function handleSubmit() {
  const err = formError.value
  if (err) {
    uni.showToast({ title: err, icon: 'none' })
    return
  }
  if (!orderToken)
    await fetchToken()
  if (!orderToken) {
    uni.showToast({ title: '提交凭证获取失败，请重试', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const orderId = await staffCreateOrder({
      memberId: selectedMember.value?.memberId,
      items: items.value,
      orderToken,
    })
    try {
      await staffPayOrder(orderId, payType.value)
      // 收款成功：本地即时更新余额展示 + 重置表单，凭证换新供下一单（连续收款）
      if (selectedMember.value && payType.value === '1') {
        selectedMember.value = {
          ...selectedMember.value,
          balance: Number((memberBalance.value - totalAmount.value).toFixed(2)),
        }
      }
      uni.showToast({ title: `收款成功 ¥${totalAmount.value.toFixed(2)}`, icon: 'none' })
      items.value = []
      selectedMember.value = undefined
      payType.value = '2'
      fetchToken()
    }
    catch {
      // 开单成功但收款失败：待收款单已留存（今日流水可见，本期页面后置），凭证换新供重试
      uni.showToast({ title: '已开单待收款，收款失败请重试或查看今日流水', icon: 'none' })
      fetchToken()
    }
  }
  catch {
    // 开单失败（含凭证失效）后换新凭证，用户修正后可直接重提
    fetchToken()
  }
  finally {
    submitting.value = false
  }
}

/** 手机号脱敏：138****6688 */
function maskedMobile(m?: string) {
  return m?.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') || ''
}
</script>

<template>
  <view class="box-border min-h-screen bg-cream">
    <!-- 页面内容区（设计稿 39:497：pad 28rpx，卡片间 16rpx）；导航栏为小程序原生导航 -->
    <view class="flex flex-col gap-[16rpx] px-[28rpx] pb-[240rpx] pt-[20rpx]">
      <!-- 卡1 会员（可选，散客可跳过） -->
      <view class="flex flex-col gap-[14rpx] rounded-[32rpx] bg-white px-[24rpx] py-[20rpx] shadow-[0_12rpx_32rpx_-8rpx_rgba(255,140,64,0.08)]">
        <view class="flex items-baseline gap-[6rpx]">
          <text class="text-[26rpx] text-[#2d2a32] font-semibold">1 · 会员</text>
          <text class="ml-[12rpx] text-[18rpx] text-[#b5b2c2]">可选，散客可跳过</text>
        </view>

        <!-- 选择行：未选灰态 / 已选奶油底橙描边（设计稿 39:497 卡1） -->
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
                {{ selectedMember.nickName || '未设置昵称' }} {{ maskedMobile(selectedMember.mobile) }}
              </text>
              <text class="truncate text-[18rpx] text-[#8a8698]">
                余额 ¥{{ memberBalance.toFixed(2) }} · 积分 {{ Number(selectedMember.pointBalance ?? 0).toFixed(2) }}
              </text>
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

      <!-- 卡2 消费明细（逐笔录入 + 自动合计） -->
      <view class="flex flex-col gap-[14rpx] rounded-[32rpx] bg-white px-[24rpx] py-[20rpx] shadow-[0_12rpx_32rpx_-8rpx_rgba(255,140,64,0.08)]">
        <view class="flex items-baseline gap-[6rpx]">
          <text class="text-[26rpx] text-[#2d2a32] font-semibold">2 · 消费明细</text>
          <text class="ml-[12rpx] text-[18rpx] text-[#b5b2c2]">单价 × 数量，名称选填</text>
        </view>

        <!-- 明细行（设计稿：r10 #FAF8FC 底，左名称+单价×数量，右行金额） -->
        <view v-if="items.length" class="flex flex-col gap-[12rpx]">
          <view
            v-for="(it, i) in items"
            :key="i"
            class="flex items-center gap-[16rpx] rounded-[20rpx] bg-[#faf8fc] px-[20rpx] py-[16rpx]"
          >
            <view class="min-w-0 flex flex-1 flex-col gap-[6rpx]">
              <text class="truncate text-[24rpx]" :class="it.itemName ? 'text-[#2d2a32]' : 'text-[#b5b2c2]'">
                {{ it.itemName || '（未命名）' }}
              </text>
              <text class="text-[20rpx] text-[#8a8698]">¥{{ it.unitPrice.toFixed(2) }} × {{ it.quantity }}</text>
            </view>
            <text class="shrink-0 text-[24rpx] text-[#2d2a32] font-medium">¥{{ (it.unitPrice * it.quantity).toFixed(2) }}</text>
          </view>
        </view>

        <!-- 分隔线 + 合计行 -->
        <template v-if="items.length">
          <view class="h-[1rpx] bg-[#f4f2f8]" />
          <view class="flex items-center justify-between">
            <text class="text-[20rpx] text-[#8a8698]">订单总额（自动合计）</text>
            <text class="text-[26rpx] text-[#2d2a32] font-semibold">¥{{ totalAmount.toFixed(2) }}</text>
          </view>
        </template>

        <!-- 添加一笔：未展开时是入口按钮；展开时是行内表单（设计稿 107:1） -->
        <view
          v-if="!adding"
          class="h-[64rpx] center border-[1.5px] border-[#ff7a29] rounded-[20rpx] bg-white"
          hover-class="opacity-90"
          :hover-stay-time="60"
          @click="openAdding"
        >
          <text class="text-[24rpx] text-[#ff7a29] font-medium">＋ 添加一笔</text>
        </view>
        <view
          v-else
          class="flex flex-col gap-[16rpx] border-[1.5px] border-[#ff7a29] rounded-[24rpx] bg-[#fff8f1] px-[20rpx] py-[20rpx]"
        >
          <!-- 名称（选填） -->
          <view class="h-[68rpx] flex items-center border border-[#f0edf5] rounded-[20rpx] bg-white px-[24rpx]">
            <input
              v-model="draftName"
              class="h-full min-w-0 flex-1 text-[24rpx] text-[#2d2a32]"
              placeholder="名称（选填）"
              placeholder-class="text-[#b5b2c2]"
              :maxlength="50"
            >
          </view>
          <!-- 价格 + 数量步进器 -->
          <view class="flex items-center gap-[16rpx]">
            <view class="h-[68rpx] flex flex-1 items-center gap-[12rpx] border border-[#f0edf5] rounded-[20rpx] bg-white px-[24rpx]">
              <text class="text-[28rpx] text-[#ff7a29] font-semibold">¥</text>
              <input
                v-model="draftPrice"
                class="h-full min-w-0 flex-1 text-[28rpx] text-[#2d2a32]"
                type="digit"
                placeholder="0.00"
                placeholder-class="text-[#b5b2c2]"
              >
              <text class="shrink-0 text-[18rpx] text-[#b5b2c2]">单价</text>
            </view>
            <view class="h-[68rpx] flex shrink-0 items-center gap-[12rpx] rounded-[20rpx] bg-[#f5f2f8] px-[12rpx]">
              <view
                class="h-[52rpx] w-[56rpx] center rounded-[16rpx] bg-white"
                hover-class="bg-[#f4f2f8]"
                :hover-stay-time="60"
                @click="stepQty(-1)"
              >
                <text class="text-[28rpx] text-[#8a8698]">−</text>
              </view>
              <text class="w-[48rpx] text-center text-[26rpx] text-[#2d2a32] font-semibold">{{ draftQty }}</text>
              <view
                class="h-[52rpx] w-[56rpx] center rounded-[16rpx] bg-[#fff0e4]"
                hover-class="bg-[#ffe4d0]"
                :hover-stay-time="60"
                @click="stepQty(1)"
              >
                <text class="text-[28rpx] text-[#ff7a29]">＋</text>
              </view>
            </view>
          </view>
          <!-- 操作行：小计预览 + 取消 + 添加 -->
          <view class="flex items-center justify-between">
            <view class="flex items-baseline gap-[8rpx]">
              <text class="text-[18rpx] text-[#8a8698]">本笔小计</text>
              <text class="text-[28rpx] text-[#ff7a29] font-bold">¥{{ draftSubtotal.toFixed(2) }}</text>
            </view>
            <view class="flex items-center gap-[16rpx]">
              <view
                class="h-[64rpx] w-[144rpx] center border border-[#f0edf5] rounded-[32rpx] bg-white"
                hover-class="bg-[#faf8fb]"
                :hover-stay-time="60"
                @click="cancelAdding"
              >
                <text class="text-[24rpx] text-[#8a8698]">取消</text>
              </view>
              <view
                class="h-[64rpx] w-[176rpx] center rounded-[32rpx] from-[#ff9a3d] to-[#ff6b5c] bg-gradient-to-r"
                hover-class="opacity-90"
                :hover-stay-time="60"
                @click="confirmAdding"
              >
                <text class="text-[24rpx] text-white font-semibold">添加</text>
              </view>
            </view>
          </view>
        </view>

        <text class="text-[18rpx] text-[#b5b2c2]">逐笔录入系统自动合计，店员只录入不计算</text>
      </view>

      <!-- 卡3 优惠券（券域 P3 前 stub：显示上线提示，不参与结算，详设 4.4） -->
      <view class="flex flex-col gap-[14rpx] rounded-[32rpx] bg-white px-[24rpx] py-[20rpx] shadow-[0_12rpx_32rpx_-8rpx_rgba(255,140,64,0.08)]">
        <view class="flex items-baseline gap-[6rpx]">
          <text class="text-[26rpx] text-[#2d2a32] font-semibold">3 · 优惠券</text>
          <text class="ml-[12rpx] text-[18rpx] text-[#b5b2c2]">每单限一张</text>
        </view>
        <view class="flex items-center gap-[20rpx] border border-[#f0edf5] rounded-[24rpx] border-dashed bg-[#faf8fc] px-[24rpx] py-[20rpx]">
          <view class="h-[68rpx] w-[68rpx] center shrink-0 rounded-[20rpx] bg-[#f5f2f8]">
            <view class="i-my-icons-coupon text-[28rpx] text-[#b5b2c2]" />
          </view>
          <text class="min-w-0 flex-1 text-[22rpx] text-[#b5b2c2]">优惠券功能上线后开放，本期不参与结算</text>
        </view>
      </view>

      <!-- 应付金额块（设计稿 39:497：应付 + 本单积分预估） -->
      <view class="flex flex-col gap-[10rpx] rounded-[32rpx] bg-white px-[24rpx] py-[20rpx] shadow-[0_12rpx_32rpx_-8rpx_rgba(255,140,64,0.12)]">
        <view class="flex items-center justify-between">
          <text class="text-[24rpx] text-[#2d2a32] font-semibold">应付金额</text>
          <text class="text-[40rpx] text-[#ff7a29] font-bold">¥{{ totalAmount.toFixed(2) }}</text>
        </view>
        <view class="flex items-center justify-between">
          <text class="text-[20rpx] text-[#8a8698]">本单积分（应付等额预估）</text>
          <text class="text-[20rpx] text-[#34c77b]">{{ totalAmount.toFixed(2) }}</text>
        </view>
      </view>

      <!-- 卡4 支付方式 -->
      <view class="flex flex-col gap-[14rpx] rounded-[32rpx] bg-white px-[24rpx] py-[20rpx] shadow-[0_12rpx_32rpx_-8rpx_rgba(255,140,64,0.08)]">
        <text class="text-[26rpx] text-[#2d2a32] font-semibold">4 · 支付方式</text>
        <view
          class="flex items-center gap-[20rpx] border-[1.5px] border-[#ff7a29] rounded-[24rpx] bg-[#fff8f1] px-[24rpx] py-[20rpx]"
          hover-class="opacity-90"
          :hover-stay-time="60"
          @click="showPayTypePicker = true"
        >
          <view
            class="h-[68rpx] w-[68rpx] center shrink-0 rounded-full text-[26rpx] font-semibold"
            :style="{ background: currentPayMeta.bg, color: currentPayMeta.color }"
          >
            {{ currentPayMeta.char }}
          </view>
          <view class="min-w-0 flex flex-1 flex-col gap-[8rpx]">
            <text class="text-[24rpx] text-[#2d2a32] font-medium">{{ currentPayMeta.label }}</text>
            <text class="truncate text-[18rpx] text-[#8a8698]">{{ currentPayMeta.desc }}</text>
          </view>
          <text class="shrink-0 text-[20rpx] text-[#ff7a29]">已选</text>
          <view class="i-my-icons-chevron-right shrink-0 text-[20rpx] text-[#b5b2c2]" />
        </view>
      </view>
    </view>

    <!-- 底部操作栏（设计稿 39:497）：白底 + 渐变确认按钮 -->
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
          {{ submitting ? '收款中…' : `确认收款 ¥${totalAmount.toFixed(2)}` }}
        </view>
      </view>
    </view>

    <!-- 弹层：选会员 / 选支付方式 -->
    <MemberPicker v-model="showMemberPicker" :selected-id="selectedMember?.memberId" @picked="onMemberPicked" />
    <PayTypePicker
      v-model="showPayTypePicker"
      title="选择支付方式"
      subtitle="微信 / 支付宝 / 现金为线下收款标记，平台不经手资金"
      :options="payTypeOptions"
      :current="payType"
      @picked="onPayTypePicked"
    />
  </view>
</template>
