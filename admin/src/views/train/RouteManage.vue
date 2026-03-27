// 线路管理页面（占位）

<template>
  <div class="route-manage">
    <div class="page-header">
      <h1 class="page-title">线路管理</h1>
      <p class="page-subtitle">管理列车运行线路</p>
    </div>

    <div class="coming-soon">
      <icon-route :size="64" />
      <h2>功能开发中</h2>
      <p>线路管理功能正在开发中，敬请期待</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { h } from 'vue'

const IconRoute = {
  template: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.29c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/></svg>`
}
</script>

<style scoped lang="scss">
.route-manage {
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
