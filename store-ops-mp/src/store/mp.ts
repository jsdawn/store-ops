import type { IMpMember } from '@/api/mp'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

/**
 * mp 端会话状态：当前门店（扫码 scene）、补绑中的 openId、会员档案
 *
 * 注意：token 本体存 tokenStore（single 模式，header Mp-Token 由拦截器注入），
 * 本 store 只存业务态。
 */
export const useMpStore = defineStore(
  'mp',
  () => {
    /** 当前门店ID（扫码 scene 解析结果，雪花ID保持字符串防精度丢失，未扫码为空） */
    const storeId = ref<string>()
    /** 登录未命中档案时下发的微信 openId（用于 bind-mobile） */
    const openId = ref('')
    /** 会员档案（登录闭环完成后写入） */
    const member = ref<IMpMember>()

    /** 已完成登录闭环（token 有效性另看 tokenStore.hasLogin） */
    const hasSession = computed(() => !!member.value)

    function setStore(id?: string) {
      storeId.value = id
    }

    function setOpenId(id: string) {
      openId.value = id
    }

    function setSession(m: IMpMember) {
      member.value = m
    }

    function clearSession() {
      member.value = undefined
      openId.value = ''
    }

    return { storeId, openId, member, hasSession, setStore, setOpenId, setSession, clearSession }
  },
  {
    persist: true,
  },
)
