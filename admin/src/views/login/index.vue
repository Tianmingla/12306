// 登录页面

<template>
  <div class="login-container">
    <div class="login-bg">
      <div class="train-track"></div>
      <div class="train-track track-2"></div>
    </div>

    <div class="login-card">
      <div class="login-header">
        <div class="logo">
          <icon-train :size="48" />
        </div>
        <h1 class="title">铁路运营管理系统</h1>
        <p class="subtitle">Railway Operation Management System</p>
      </div>

      <a-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        layout="vertical"
        @submit-success="handleLogin"
      >
        <a-form-item field="username" hide-label>
          <a-input
            v-model="formData.username"
            placeholder="请输入用户名"
            size="large"
            allow-clear
          >
            <template #prefix>
              <icon-user :size="18" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item field="password" hide-label>
          <a-input-password
            v-model="formData.password"
            placeholder="请输入密码"
            size="large"
            allow-clear
          >
            <template #prefix>
              <icon-lock :size="18" />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item field="role" hide-label>
          <a-select
            v-model="formData.role"
            placeholder="请选择登录角色"
            size="large"
          >
            <a-option value="admin">
              <div class="role-option">
                <icon-user-group :size="18" />
                <span>系统管理员</span>
              </div>
            </a-option>
            <a-option value="operator">
              <div class="role-option">
                <icon-customer-service :size="18" />
                <span>运营人员</span>
              </div>
            </a-option>
          </a-select>
        </a-form-item>

        <div class="form-options">
          <a-checkbox v-model="rememberMe">记住登录状态</a-checkbox>
        </div>

        <a-button
          type="primary"
          html-type="submit"
          :loading="loading"
          long
          size="large"
        >
          {{ loading ? '登录中...' : '登 录' }}
        </a-button>
      </a-form>

      <div class="login-footer">
        <p>© 2024 中国铁路 版权所有</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import type { FormInstance } from '@arco-design/web-vue'
import { useUserStore } from '@/store/user'
import { login as loginApi } from '@/api/user'
import {
  IconUser,
  IconLock,
  IconUserGroup,
  IconCustomerService,
} from '@arco-design/web-vue/es/icon'

// 自定义列车图标
const IconTrain = {
  template: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C8 2 4 2.5 4 6v9.5c0 1.93 1.57 3.5 3.5 3.5L6 20.5v.5h2l1-2h6l1 2h2v-.5L16.5 19c1.93 0 3.5-1.57 3.5-3.5V6c0-3.5-4-4-8-4zm-1.5 14.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm7 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM18 10H6V6h12v4z"/></svg>`
}

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const rememberMe = ref(false)

const formData = reactive({
  username: 'admin',
  password: '123456',
  role: 'admin',
})

const rules = {
  username: [
    { required: true, message: '请输入用户名' },
    { minLength: 3, message: '用户名至少3个字符' },
  ],
  password: [
    { required: true, message: '请输入密码' },
    { minLength: 6, message: '密码至少6个字符' },
  ],
  role: [
    { required: true, message: '请选择登录角色' },
  ],
}

const handleLogin = async () => {
  loading.value = true

  try {
    const res = await loginApi({
      username: formData.username,
      password: formData.password,
    })

    if (res.code === 200 || res.code === 0) {
      userStore.setToken(res.data.token)
      userStore.setUserInfo({
        id: res.data.userId,
        username: res.data.username || formData.username,
        role: formData.role,
        roleName: formData.role === 'admin' ? '系统管理员' : '运营人员',
      })

      Message.success('登录成功')
      router.push('/dashboard')
    } else {
      Message.error(res.message || '登录失败')
    }
  } catch (error: any) {
    Message.error(error.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-container {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, $bg-dark 0%, lighten($bg-dark, 5%) 100%);
}

.login-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;

  .train-track {
    position: absolute;
    bottom: 15%;
    left: -100%;
    width: 300%;
    height: 2px;
    background: repeating-linear-gradient(
      90deg,
      transparent,
      transparent 50px,
      $border-color 50px,
      $border-color 100px
    );
    opacity: 0.3;

    &.track-2 {
      bottom: 35%;
    }
  }
}

.login-card {
  width: 420px;
  padding: 48px 40px;
  background: $bg-card;
  border-radius: $border-radius-lg;
  border: 1px solid $border-color;
  box-shadow: $shadow-lg;
  position: relative;
  z-index: 1;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 4px;
    background: linear-gradient(90deg, $primary-color, $secondary-color);
    border-radius: $border-radius-lg $border-radius-lg 0 0;
  }
}

.login-header {
  text-align: center;
  margin-bottom: 40px;

  .logo {
    width: 80px;
    height: 80px;
    margin: 0 auto 20px;
    background: linear-gradient(135deg, $primary-color 0%, #8B1A2D 100%);
    border-radius: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
  }

  .title {
    font-size: 24px;
    font-weight: 600;
    color: $text-primary;
    margin: 0 0 8px;
  }

  .subtitle {
    font-size: 12px;
    color: $text-muted;
    margin: 0;
    letter-spacing: 1px;
  }
}

.role-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-options {
  display: flex;
  justify-content: space-between;
  margin-bottom: 24px;

  :deep(.arco-checkbox) {
    color: $text-secondary;
  }
}

:deep(.arco-form-item) {
  margin-bottom: 20px;
}

:deep(.arco-input),
:deep(.arco-select-view) {
  background: $bg-dark;
  border-color: $border-color;

  &:hover,
  &:focus-within {
    border-color: $primary-color;
  }
}

:deep(.arco-btn-primary) {
  background: linear-gradient(135deg, $primary-color 0%, #8B1A2D 100%);
  border: none;
  height: 44px;
  font-size: 16px;

  &:hover {
    background: linear-gradient(135deg, lighten($primary-color, 5%) 0%, #8B1A2D 100%);
  }
}

.login-footer {
  text-align: center;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid $border-color;

  p {
    font-size: 12px;
    color: $text-muted;
    margin: 0;
  }
}
</style>
