import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/types'

interface UserInfo extends User {
  role: string
  roleName: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('admin_token') || '')
  const userInfo = ref<UserInfo | null>(null)

  // 是否已登录
  const isLoggedIn = computed(() => !!token.value)

  // 是否是管理员
  const isAdmin = computed(() => userInfo.value?.role === 'admin')

  const setToken = (newToken: string) => {
    token.value = newToken
    localStorage.setItem('admin_token', newToken)
  }

  const setUserInfo = (info: UserInfo) => {
    userInfo.value = info
  }

  const logout = () => {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('admin_token')
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    isAdmin,
    setToken,
    setUserInfo,
    logout,
  }
})
