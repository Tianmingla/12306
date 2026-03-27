<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

onMounted(() => {
  // 初始化时检查登录状态
  if (userStore.token && !userStore.userInfo) {
    // 可以在这里获取用户信息
  }
})
</script>

<style>
#app {
  width: 100%;
  height: 100%;
}
</style>
