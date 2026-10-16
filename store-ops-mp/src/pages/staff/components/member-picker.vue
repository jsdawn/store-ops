<script lang="ts" setup>
import type { IMpStaffMemberBrief } from '@/api/mp'
import { searchStaffMembers } from '@/api/mp'
import { debounce } from '@/utils/debounce'

/** 当前已选会员ID（弹层内高亮展示，未选时无高亮） */
withDefaults(defineProps<{ selectedId?: string }>(), { selectedId: '' })

/**
 * 选择会员底部弹层（设计稿 39:619「12 选择会员」员工端复用版）：
 * 搜索手机号 / 卡号 / 昵称，点选即回填并关闭（详设开发说明：无散客项）。
 * 本店会员池由后端按员工所属门店圈定，前端不做归属判断。
 */
const emit = defineEmits<{ picked: [member: IMpStaffMemberBrief] }>()

const visible = defineModel<boolean>({ default: false })

const keyword = ref('')
const members = ref<IMpStaffMemberBrief[]>([])
const loading = ref(false)

/** 关键字防抖搜索：停止输入 300ms 后再请求（避免逐字符打接口） */
const searchDebounced = debounce(() => {
  fetchMembers()
}, 300)

watch(visible, (v) => {
  if (v) {
    keyword.value = ''
    fetchMembers()
  }
  else {
    // 关闭时取消挂起的防抖请求，避免打开后用旧关键字发起请求
    searchDebounced.cancel()
  }
})

watch(keyword, () => {
  searchDebounced()
})

async function fetchMembers() {
  loading.value = true
  try {
    members.value = await searchStaffMembers(keyword.value.trim())
  }
  catch {
    // 业务错误已由请求层 toast
  }
  finally {
    loading.value = false
  }
}

/** 手机号脱敏：138****6688 */
function maskedMobile(m?: string) {
  return m?.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') || '未绑定手机号'
}

/** 头像底色轮换（设计稿：橙奶油 / 蓝 / 绿） */
const avatarStyles = [
  { bg: '#ffe3d1', color: '#ff7a29' },
  { bg: '#e8f1ff', color: '#4d9fff' },
  { bg: '#e6f9ee', color: '#34c77b' },
] as const

function pick(m: IMpStaffMemberBrief) {
  emit('picked', m)
  visible.value = false
}
</script>

<template>
  <wd-popup
    v-model="visible"
    position="bottom"
    lock-scroll
    custom-style="height: 72vh; background: #ffffff; border-radius: 40rpx 40rpx 0 0; box-shadow: 0 -6px 24px rgba(46, 41, 51, 0.12);"
    :safe-area-inset-bottom="true"
  >
    <view class="h-full flex flex-col gap-[24rpx] px-[40rpx] pb-[12rpx] pt-[16rpx]">
      <!-- 拖拽指示条 -->
      <view class="mx-auto h-[8rpx] w-[72rpx] rounded-[4rpx] bg-[#e5e2ec]" />

      <!-- 标题行：左标题 + 右上圆形关闭钮 -->
      <view class="flex items-center justify-between pt-[20rpx]">
        <view class="flex flex-col gap-[6rpx]">
          <text class="text-[32rpx] text-[#2d2a32] font-bold">选择会员</text>
          <text class="text-[20rpx] text-[#8a8698]">本店会员池 · 点选即回填</text>
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

      <!-- 搜索框（设计稿 39:634：#F5F2F8 高 84rpx 圆角放大镜 + 占位） -->
      <view class="h-[84rpx] flex items-center gap-[16rpx] rounded-[24rpx] bg-[#f5f2f8] px-[28rpx]">
        <view class="i-my-icons-search shrink-0 text-[28rpx]" />
        <input
          v-model="keyword"
          class="h-full min-w-0 flex-1 text-[24rpx] text-[#2d2a32]"
          placeholder="搜索手机号 / 姓名"
          placeholder-class="text-[#b5b2c2]"
          confirm-type="search"
        >
      </view>

      <!-- 会员行列表：占满弹层剩余高度内滚动（弹层整体固定 72vh） -->
      <view class="min-h-0 flex flex-1 flex-col gap-[20rpx] overflow-y-auto pb-[8rpx]">
        <template v-if="loading">
          <view v-for="i in 3" :key="i" class="h-[120rpx] animate-pulse rounded-[28rpx] bg-[#faf8fb]" />
        </template>

        <view v-else-if="!members.length" class="py-[80rpx] text-center text-[24rpx] text-[#8a8698]">
          没有匹配的本店会员
        </view>

        <view
          v-for="(m, i) in members"
          :key="m.memberId"
          class="flex items-center gap-[24rpx] border-[1.5px] rounded-[28rpx] px-[28rpx] py-[24rpx]"
          :class="m.memberId === selectedId ? 'border-[#ff7a29] bg-[#fff8f1]' : 'border-[#f0edf5] bg-white'"
          hover-class="bg-[#faf8fb]"
          :hover-stay-time="60"
          @click="pick(m)"
        >
          <!-- 头像：昵称首字（设计稿 39:640） -->
          <view
            class="h-[72rpx] w-[72rpx] center shrink-0 rounded-full text-[26rpx] font-semibold"
            :style="{ background: avatarStyles[i % 3].bg, color: avatarStyles[i % 3].color }"
          >
            {{ (m.nickName || m.mobile || '客')[0] }}
          </view>

          <view class="min-w-0 flex flex-1 flex-col gap-[8rpx]">
            <text class="truncate text-[26rpx] text-[#2d2a32] font-semibold">
              {{ m.nickName || '未设置昵称' }} {{ maskedMobile(m.mobile) }}
            </text>
            <text class="truncate text-[20rpx] text-[#8a8698]">
              余额 ¥{{ Number(m.balance ?? 0).toFixed(2) }} · 积分 {{ Number(m.pointBalance ?? 0).toFixed(2) }}
            </text>
          </view>

          <!-- radio：已选橙底白勾 / 未选灰描边圆环（点选即回填，与切店弹层同款） -->
          <view
            class="h-[36rpx] w-[36rpx] shrink-0 rounded-full"
            :class="m.memberId === selectedId ? 'center border-[1.5px] border-[#ff7a29] bg-[#ff7a29]' : 'border-[1.5px] border-[#c9c6d4] bg-white'"
          >
            <view v-if="m.memberId === selectedId" class="i-my-icons-check text-[20rpx] text-white" />
          </view>
        </view>
      </view>
    </view>
  </wd-popup>
</template>
