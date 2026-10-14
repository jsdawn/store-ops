<script lang="ts" setup>
import { bindMobile, decodePhone, getMe } from "@/api/mp";
import { useMpStore, useTokenStore } from "@/store";
import MpFormRow from "./components/mp-form-row.vue";

definePage({
  style: {
    navigationBarTitleText: "完善会员信息",
    // 设计稿导航区白底黑字（返回按钮系统默认样式，不做自定义导航）
    navigationBarBackgroundColor: "#ffffff",
    navigationBarTextStyle: "black",
  },
});

const mpStore = useMpStore();
const tokenStore = useTokenStore();

/** 编辑模式（会员中心点名称进入）：昵称可改，手机号锁定只读 */
const isEdit = ref(false);

const nickName = ref("");
const mobile = ref("");
const loading = ref(false);

onLoad((query) => {
  if (query?.mode === "edit") {
    isEdit.value = true;
    uni.setNavigationBarTitle({ title: "编辑会员信息" });
    fillFromProfile();
  }
});

/**
 * 编辑模式：查询会员档案回填（昵称可改，手机号锁定只读）。
 * 数据以后端为准（本地 store 可能过期）；401 由请求层统一清态跳登录。
 */
async function fillFromProfile() {
  if (!mpStore.storeId) {
    uni.showToast({ title: "登录态缺失，请重新扫码进入", icon: "none" });
    uni.redirectTo({ url: "/pages/login/index" });
    return;
  }
  try {
    const m = await getMe(mpStore.storeId);
    // 自查档案补齐 openId（老版本登录链路未写入时自愈，bind-mobile 必需）
    if (m.openId)
      mpStore.setOpenId(m.openId);
    mpStore.setSession(m);
    nickName.value = m.nickName ?? "";
    mobile.value = m.mobile ?? "";
  } catch {
    // 业务错误已由请求层 toast
  }
}

/** 前置校验：昵称必填 + 登录态存在 */
function preCheck(): string | null {
  const name = nickName.value.trim();
  if (!name) {
    uni.showToast({ title: "请输入昵称", icon: "none" });
    return null;
  }
  if (!mpStore.storeId || !mpStore.openId) {
    uni.showToast({ title: "登录态缺失，请重新扫码进入", icon: "none" });
    uni.redirectTo({ url: "/pages/login/index" });
    return null;
  }
  return name;
}

/** 登录闭环：bind-mobile 补绑/建档/更新 → 存 token → 回会员中心 */
async function handleConfirm() {
  const name = preCheck();
  if (!name) return;
  if (!/^1\d{10}$/.test(mobile.value.trim())) {
    uni.showToast({ title: "手机号格式不正确", icon: "none" });
    return;
  }
  if (loading.value) return;
  loading.value = true;
  try {
    const res = await bindMobile({
      storeId: mpStore.storeId,
      openId: mpStore.openId,
      mobile: mobile.value.trim(),
      nickName: name,
    });

    tokenStore.setTokenInfo({
      token: res.token,
      expiresIn: res.expiresIn ?? 0,
    });
    mpStore.setSession(res.member);
    uni.switchTab({ url: '/pages/member/member' })
  } catch {
    // HttpError 已由请求层归一处理
  } finally {
    loading.value = false;
  }
}

/**
 * 微信一键获取手机号（行内按钮 open-type="getPhoneNumber"）：
 * 授权回调 code → 后端解码 → 回填输入框，确认时统一走 bind-mobile 提交
 */
async function onGetPhone(e: { detail: { errMsg?: string, code?: string } }) {
  const { errMsg, code } = e.detail ?? {};
  if (errMsg !== "getPhoneNumber:ok" || !code) {
    uni.showToast({ title: "未获取微信手机号授权", icon: "none" });
    return;
  }
  try {
    // code 单次使用（5 分钟有效），拿到即解码回填
    const res = await decodePhone({ code });
    mobile.value = res.phone;
    uni.showToast({ title: "手机号已获取", icon: "success" });
  } catch {
    // 解码失败（过期/已用）由请求层 toast，可重新点击获取
  }
}
</script>

<template>
  <!-- 设计稿 186:28 设置昵称和手机号：奶油底 + 白色表单卡 + 底部白栏渐变确认按钮 -->
  <view class="box-border min-h-screen bg-cream pb-[240rpx]">
    <!-- 导航分隔线（原生导航栏无下边框，按设计稿补 #F0EDF5） -->
    <view class="h-[2rpx] bg-[#f0edf5]" />

    <!-- 页面内容区：padding 28rpx / 提示与卡片间距 24rpx（设计稿 gap 12） -->
    <view class="px-[28rpx] pt-[28rpx]">
      <text class="text-[22rpx] text-[#8a8698]">
        {{
          isEdit
            ? "修改昵称后点击确认保存"
            : "首次登录，完善昵称与手机号后即可使用会员服务"
        }}
      </text>

      <!-- 信息表单卡：白底圆角 32rpx，行内边距由行组件承担 -->
      <view class="mt-[24rpx] rounded-[32rpx] bg-white px-[28rpx] py-[4rpx]">
        <MpFormRow label="昵称" required>
          <wd-input
            v-model="nickName"
            type="nickname"
            placeholder="小王"
            placeholder-style="color:#b5b2c2"
            :maxlength="20"
            compact
            custom-input-class="text-[26rpx] text-[#2d2a32] font-medium"
          />
        </MpFormRow>
        <view class="h-[2rpx] bg-[#f4f2f8]" />
        <MpFormRow label="手机号" required>
          <!-- 编辑模式：手机号锁定只读（更换手机号涉及档案换绑，本期不做） -->
          <text v-if="isEdit" class="text-[26rpx] text-[#2d2a32] font-medium">
            {{ mobile }}
          </text>
          <!-- 新建模式：输入框 + 行内「获取」按钮（微信手机号快速验证） -->
          <view v-else class="w-full flex items-center">
            <wd-input
              v-model="mobile"
              class="flex-1"
              type="number"
              placeholder="13866886688"
              placeholder-style="color:#b5b2c2"
              :maxlength="11"
              compact
              custom-class="!px-0 !bg-transparent"
              custom-input-class="text-[26rpx] text-[#2d2a32] font-medium"
            />
            <!-- 原生 button：open-type 必须挂在原生节点上；样式已由 base.scss 归零 -->
            <button
              class="ml-[12rpx] h-[56rpx] inline-flex shrink-0 items-center rounded-full bg-[#fff1e6] px-[26rpx] text-[24rpx] text-primary leading-none"
              hover-class="opacity-70"
              open-type="getPhoneNumber"
              @getphonenumber="onGetPhone"
            >
              获取
            </button>
          </view>
        </MpFormRow>
      </view>
    </view>

    <!-- 底部操作栏：白底（含 home 区），确认按钮 44rpx 上边距 + 安全区 -->
    <view
      class="fixed inset-x-0 bottom-0 bg-white px-[28rpx] pt-[44rpx] pb-safe"
    >
      <fg-gradient-button :loading="loading" @click="handleConfirm">
        确认
      </fg-gradient-button>
    </view>
  </view>
</template>
