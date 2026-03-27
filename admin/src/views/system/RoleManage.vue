// 角色管理页面（占位）

<template>
  <div class="role-manage">
    <div class="page-header">
      <h1 class="page-title">角色管理</h1>
      <p class="page-subtitle">管理系统角色权限</p>
    </div>

    <div class="coming-soon">
      <icon-user-group :size="64" />
      <h2>功能开发中</h2>
      <p>角色管理功能正在开发中，敬请期待</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { IconUserGroup } from '@arco-design/web-vue/es/icon'
</script>

<style scoped lang="scss">
.role-manage {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.coming-soon {
  text-align: center;
  color: $text-muted;

  h2 {
    font-size: 20px;
    color: $text-secondary;
    margin: 24px 0 8px;
  }

  p {
    font-size: 14px;
  }
}
</style>
