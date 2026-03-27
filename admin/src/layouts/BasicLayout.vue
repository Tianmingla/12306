<template>
  <div class="basic-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: isCollapse }">
      <div class="logo">
        <icon-train :size="28" />
        <span v-show="!isCollapse" class="logo-text">铁路运营管理</span>
      </div>
      <a-menu
        :default-selected-keys="[activeMenu]"
        :collapsed="isCollapse"
        :collapsed-width="64"
        :open-keys="openKeys"
        @sub-menu-click="handleSubMenuClick"
        auto-open-selected
        theme="dark"
      >
        <a-menu-item key="/dashboard" @click="navigateTo('/dashboard')">
          <template #icon><icon-dashboard /></template>
          数据统计
        </a-menu-item>

        <a-sub-menu key="train">
          <template #icon><icon-train /></template>
          <template #title>车票管理</template>
          <a-menu-item key="/train/list" @click="navigateTo('/train/list')">车次管理</a-menu-item>
          <a-menu-item key="/train/station" @click="navigateTo('/train/station')">站点管理</a-menu-item>
          <a-menu-item key="/train/route" @click="navigateTo('/train/route')">线路管理</a-menu-item>
        </a-sub-menu>

        <a-sub-menu key="order">
          <template #icon><icon-file /></template>
          <template #title>订单管理</template>
          <a-menu-item key="/order/list" @click="navigateTo('/order/list')">订单列表</a-menu-item>
          <a-menu-item key="/order/refund" @click="navigateTo('/order/refund')">退款管理</a-menu-item>
        </a-sub-menu>

        <a-sub-menu key="system">
          <template #icon><icon-settings /></template>
          <template #title>系统管理</template>
          <a-menu-item key="/system/user" @click="navigateTo('/system/user')">用户管理</a-menu-item>
          <a-menu-item key="/system/role" @click="navigateTo('/system/role')">角色管理</a-menu-item>
          <a-menu-item key="/system/log" @click="navigateTo('/system/log')">操作日志</a-menu-item>
        </a-sub-menu>
      </a-menu>
    </aside>

    <!-- 主内容区 -->
    <div class="main-container">
      <!-- 顶部导航 -->
      <header class="header">
        <div class="header-left">
          <div class="collapse-btn" @click="isCollapse = !isCollapse">
            <icon-menu-fold v-if="!isCollapse" :size="20" />
            <icon-menu-unfold v-else :size="20" />
          </div>
          <a-breadcrumb>
            <a-breadcrumb-item>
              <icon-home />
            </a-breadcrumb-item>
            <a-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.meta?.title }}
            </a-breadcrumb-item>
          </a-breadcrumb>
        </div>

        <div class="header-right">
          <a-dropdown trigger="hover">
            <div class="user-info">
              <a-avatar :size="32" class="avatar">
                <icon-user />
              </a-avatar>
              <span class="username">{{ userStore.userInfo?.username || '管理员' }}</span>
              <icon-down :size="14" />
            </div>
            <template #content>
              <a-doption @click="handleCommand('profile')">
                <template #icon><icon-user /></template>
                个人中心
              </a-doption>
              <a-doption @click="handleCommand('password')">
                <template #icon><icon-lock /></template>
                修改密码
              </a-doption>
              <a-divider class="dropdown-divider" />
              <a-doption @click="handleCommand('logout')">
                <template #icon><icon-export /></template>
                退出登录
              </a-doption>
            </template>
          </a-dropdown>
        </div>
      </header>

      <!-- 内容区域 -->
      <main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal } from '@arco-design/web-vue'
import { useUserStore } from '@/store/user'
import {
  IconDashboard,
  IconFile,
  IconSettings,
  IconUser,
  IconLock,
  IconExport,
  IconDown,
  IconHome,
  IconMenuFold,
  IconMenuUnfold,
} from '@arco-design/web-vue/es/icon'

// 自定义列车图标
const IconTrain = {
  template: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C8 2 4 2.5 4 6v9.5c0 1.93 1.57 3.5 3.5 3.5L6 20.5v.5h2l1-2h6l1 2h2v-.5L16.5 19c1.93 0 3.5-1.57 3.5-3.5V6c0-3.5-4-4-8-4zm-1.5 14.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm7 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM18 10H6V6h12v4z"/></svg>`
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(false)
const openKeys = ref<string[]>(['train', 'order', 'system'])

const activeMenu = computed(() => route.path)

const breadcrumbs = computed(() => {
  return route.matched.filter((item) => item.meta?.title && !item.meta?.hidden)
})

// 监听路由变化，更新展开的菜单
watch(
  () => route.path,
  (path) => {
    const parentKey = path.split('/').slice(0, 2).join('/')
    if (['/train', '/order', '/system'].includes(parentKey) && !openKeys.value.includes(parentKey.slice(1))) {
      openKeys.value = [...openKeys.value, parentKey.slice(1)]
    }
  },
  { immediate: true }
)

const navigateTo = (path: string) => {
  router.push(path)
}

const handleSubMenuClick = (key: string) => {
  if (openKeys.value.includes(key)) {
    openKeys.value = openKeys.value.filter(k => k !== key)
  } else {
    openKeys.value = [...openKeys.value, key]
  }
}

const handleCommand = (command: string) => {
  switch (command) {
    case 'profile':
      router.push('/profile')
      break
    case 'password':
      router.push('/password')
      break
    case 'logout':
      Modal.warning({
        title: '退出确认',
        content: '确定要退出登录吗？',
        okText: '确定',
        cancelText: '取消',
        hideCancel: false,
        onOk: () => {
          userStore.logout()
          router.push('/login')
        },
      })
      break
  }
}
</script>

<style scoped lang="scss">
.basic-layout {
  width: 100%;
  height: 100%;
  display: flex;
}

.sidebar {
  width: 220px;
  min-width: 220px;
  height: 100%;
  background: linear-gradient(180deg, #1E293B 0%, #0F172A 100%);
  transition: width $transition, min-width $transition;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  &.collapsed {
    width: 64px;
    min-width: 64px;

    .logo-text {
      display: none;
    }
  }

  .logo {
    height: 64px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    background: linear-gradient(135deg, $primary-color 0%, #8B1A2D 100%);
    color: #fff;
    flex-shrink: 0;

    .logo-text {
      font-size: 16px;
      font-weight: 600;
      color: #fff;
      white-space: nowrap;
    }
  }

  :deep(.arco-menu) {
    background: transparent;
    flex: 1;
    overflow-y: auto;

    .arco-menu-item,
    .arco-menu-inline-header {
      color: $text-secondary;
      transition: all $transition-fast;

      &:hover {
        background: $bg-hover;
        color: $text-primary;
      }

      &.arco-menu-selected {
        background: linear-gradient(90deg, rgba($primary-color, 0.2) 0%, transparent 100%);
        color: $primary-color;
        border-left: 3px solid $primary-color;

        .arco-menu-icon {
          color: $primary-color;
        }
      }
    }

    .arco-menu-inline-header {
      .arco-menu-icon {
        color: $text-secondary;
      }
    }

    .arco-menu-inline-content {
      background: rgba(0, 0, 0, 0.2);
    }
  }
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: $bg-card;
  border-bottom: 1px solid $border-color;
  padding: 0 24px;
  flex-shrink: 0;

  .header-left {
    display: flex;
    align-items: center;
    gap: 20px;

    .collapse-btn {
      cursor: pointer;
      color: $text-secondary;
      transition: color $transition-fast;
      display: flex;
      align-items: center;

      &:hover {
        color: $primary-color;
      }
    }

    :deep(.arco-breadcrumb) {
      .arco-breadcrumb-item {
        color: $text-muted;

        &:last-child {
          color: $text-primary;
        }
      }
    }
  }

  .header-right {
    .user-info {
      display: flex;
      align-items: center;
      gap: 10px;
      cursor: pointer;
      padding: 6px 12px;
      border-radius: $border-radius;
      transition: background $transition-fast;

      &:hover {
        background: $bg-hover;
      }

      .avatar {
        background: linear-gradient(135deg, $primary-color 0%, $secondary-color 100%);
      }

      .username {
        font-size: 14px;
        color: $text-primary;
      }
    }
  }
}

.dropdown-divider {
  margin: 4px 0;
}

.main {
  flex: 1;
  background: $bg-dark;
  padding: 20px;
  overflow-y: auto;
}

// 过渡动画
.fade-enter-active,
.fade-leave-active {
  transition: opacity $transition ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
