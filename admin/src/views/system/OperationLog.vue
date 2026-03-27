// 操作日志页面（占位）

<template>
  <div class="operation-log">
    <div class="page-header">
      <h1 class="page-title">操作日志</h1>
      <p class="page-subtitle">查看系统操作记录</p>
    </div>

    <div class="coming-soon">
      <icon-file :size="64" />
      <h2>功能开发中</h2>
      <p>操作日志功能正在开发中，敬请期待</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { IconFile } from '@arco-design/web-vue/es/icon'
</script>

<style scoped lang="scss">
.operation-log {
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
