// 用户管理页面

<template>
  <div class="user-manage">
    <div class="page-header">
      <h1 class="page-title">用户管理</h1>
      <p class="page-subtitle">管理系统用户信息</p>
    </div>

    <!-- 搜索表单 -->
    <div class="search-form">
      <a-form :model="searchForm" layout="inline" auto-label-width>
        <a-form-item label="关键词">
          <a-input
            v-model="searchForm.keyword"
            placeholder="用户名/手机号/邮箱"
            allow-clear
            style="width: 200px"
          />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model="searchForm.status" placeholder="全部" allow-clear style="width: 120px">
            <a-option :value="0">正常</a-option>
            <a-option :value="1">禁用</a-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="handleSearch">
              <template #icon><icon-search /></template>
              搜索
            </a-button>
            <a-button @click="handleReset">
              <template #icon><icon-refresh /></template>
              重置
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <a-table
        :columns="columns"
        :data="tableData"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @page-change="handlePageChange"
      >
        <template #status="{ record }">
          <a-tag :color="record.status === 0 ? 'green' : 'red'">
            {{ record.status === 0 ? '正常' : '禁用' }}
          </a-tag>
        </template>
        <template #actions="{ record }">
          <a-space>
            <a-button type="text" size="small" @click="handleView(record)">
              <template #icon><icon-eye /></template>
              查看
            </a-button>
            <a-button
              type="text"
              size="small"
              :status="record.status === 0 ? 'warning' : 'success'"
              @click="handleToggleStatus(record)"
            >
              <template #icon>
                <icon-lock v-if="record.status === 0" />
                <icon-unlock v-else />
              </template>
              {{ record.status === 0 ? '禁用' : '启用' }}
            </a-button>
            <a-button type="text" size="small" @click="handleResetPassword(record)">
              <template #icon><icon-key /></template>
              重置密码
            </a-button>
          </a-space>
        </template>
      </a-table>
    </div>

    <!-- 用户详情弹窗 -->
    <a-modal
      v-model:visible="detailVisible"
      title="用户详情"
      :footer="false"
      width="600px"
    >
      <a-descriptions :column="2" bordered>
        <a-descriptions-item label="用户ID">{{ currentUser?.id }}</a-descriptions-item>
        <a-descriptions-item label="用户名">{{ currentUser?.username }}</a-descriptions-item>
        <a-descriptions-item label="手机号">{{ currentUser?.phone }}</a-descriptions-item>
        <a-descriptions-item label="邮箱">{{ currentUser?.email || '-' }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="currentUser?.status === 0 ? 'green' : 'red'">
            {{ currentUser?.status === 0 ? '正常' : '禁用' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="注册时间">{{ currentUser?.createTime }}</a-descriptions-item>
      </a-descriptions>

      <div class="passenger-section">
        <h4>乘车人信息</h4>
        <a-table
          :columns="passengerColumns"
          :data="passengers"
          :pagination="false"
          size="small"
        >
          <template #idCardType="{ record }">
            {{ getIdCardTypeName(record.idCardType) }}
          </template>
          <template #passengerType="{ record }">
            {{ getPassengerTypeName(record.passengerType) }}
          </template>
        </a-table>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import type { User, Passenger, UserQueryParams } from '@/types'
import { mockUsers, mockPassengers, mockApi } from '@/mock/data'
import {
  IconSearch,
  IconRefresh,
  IconEye,
  IconLock,
  IconUnlock,
  IconKey,
} from '@arco-design/web-vue/es/icon'

// 搜索表单
const searchForm = reactive<UserQueryParams>({
  keyword: '',
  status: undefined,
  pageNum: 1,
  pageSize: 10,
})

// 表格数据
const loading = ref(false)
const tableData = ref<User[]>([])
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

// 表格列定义
const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '用户名', dataIndex: 'username' },
  { title: '手机号', dataIndex: 'phone' },
  { title: '邮箱', dataIndex: 'email', ellipsis: true },
  { title: '状态', slotName: 'status', width: 100 },
  { title: '注册时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', slotName: 'actions', width: 250 },
]

// 用户详情
const detailVisible = ref(false)
const currentUser = ref<User>()
const passengers = ref<Passenger[]>([])

// 乘车人表格列
const passengerColumns = [
  { title: '姓名', dataIndex: 'realName' },
  { title: '证件类型', slotName: 'idCardType' },
  { title: '证件号', dataIndex: 'idCardNumber' },
  { title: '类型', slotName: 'passengerType' },
  { title: '手机号', dataIndex: 'phone' },
]

// 获取证件类型名称
const getIdCardTypeName = (type: number) => {
  const types = ['身份证', '护照', '港澳通行证', '台湾通行证']
  return types[type] || '未知'
}

// 获取乘客类型名称
const getPassengerTypeName = (type: number) => {
  const types = ['成人', '儿童', '学生', '残疾军人']
  return types[type] || '未知'
}

// 获取用户列表
const fetchUsers = async () => {
  loading.value = true
  try {
    // 模拟API请求
    await new Promise((resolve) => setTimeout(resolve, 500))

    let filteredData = [...mockUsers]

    // 关键词搜索
    if (searchForm.keyword) {
      const keyword = searchForm.keyword.toLowerCase()
      filteredData = filteredData.filter(
        (user) =>
          user.username.toLowerCase().includes(keyword) ||
          user.phone.includes(keyword) ||
          user.email?.toLowerCase().includes(keyword)
      )
    }

    // 状态筛选
    if (searchForm.status !== undefined) {
      filteredData = filteredData.filter((user) => user.status === searchForm.status)
    }

    // 分页
    const start = (pagination.current - 1) * pagination.pageSize
    const end = start + pagination.pageSize

    tableData.value = filteredData.slice(start, end)
    pagination.total = filteredData.length
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  fetchUsers()
}

// 重置
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.status = undefined
  pagination.current = 1
  fetchUsers()
}

// 分页变化
const handlePageChange = (page: number) => {
  pagination.current = page
  fetchUsers()
}

// 查看用户详情
const handleView = (user: User) => {
  currentUser.value = user
  passengers.value = mockPassengers.filter((p) => p.userId === user.id)
  detailVisible.value = true
}

// 切换用户状态
const handleToggleStatus = (user: User) => {
  Modal.warning({
    title: user.status === 0 ? '确认禁用' : '确认启用',
    content: user.status === 0 ? `确定要禁用用户 "${user.username}" 吗？` : `确定要启用用户 "${user.username}" 吗？`,
    okText: '确定',
    cancelText: '取消',
    hideCancel: false,
    onOk: () => {
      user.status = user.status === 0 ? 1 : 0
      Message.success(user.status === 0 ? '已启用' : '已禁用')
    },
  })
}

// 重置密码
const handleResetPassword = (user: User) => {
  Modal.warning({
    title: '确认重置密码',
    content: `确定要重置用户 "${user.username}" 的密码吗？重置后密码将变为 "123456"`,
    okText: '确定',
    cancelText: '取消',
    hideCancel: false,
    onOk: () => {
      Message.success('密码已重置为 123456')
    },
  })
}

onMounted(() => {
  fetchUsers()
})
</script>

<style scoped lang="scss">
.user-manage {
  .passenger-section {
    margin-top: 24px;

    h4 {
      font-size: 14px;
      font-weight: 500;
      color: $text-primary;
      margin-bottom: 12px;
    }
  }
}

:deep(.arco-table) {
  .arco-table-th {
    background: $bg-dark;
  }
}

:deep(.arco-descriptions-item-label) {
  background: $bg-dark;
}
</style>
