// 退款管理页面

<template>
  <div class="refund-manage">
    <div class="page-header">
      <h1 class="page-title">退款管理</h1>
      <p class="page-subtitle">处理用户退款申请</p>
    </div>

    <!-- 搜索表单 -->
    <div class="search-form">
      <a-form :model="searchForm" layout="inline" auto-label-width>
        <a-form-item label="订单号">
          <a-input
            v-model="searchForm.keyword"
            placeholder="请输入订单号"
            allow-clear
            style="width: 200px"
          />
        </a-form-item>
        <a-form-item label="退款状态">
          <a-select v-model="searchForm.status" placeholder="全部" allow-clear style="width: 120px">
            <a-option :value="0">待处理</a-option>
            <a-option :value="1">已通过</a-option>
            <a-option :value="2">已拒绝</a-option>
            <a-option :value="3">已完成</a-option>
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
          <a-tag :color="getStatusColor(record.status)">
            {{ getStatusName(record.status) }}
          </a-tag>
        </template>
        <template #actions="{ record }">
          <a-space v-if="record.status === 0">
            <a-button type="text" size="small" status="success" @click="handleAudit(record, true)">
              <template #icon><icon-check /></template>
              通过
            </a-button>
            <a-button type="text" size="small" status="danger" @click="handleAudit(record, false)">
              <template #icon><icon-close /></template>
              拒绝
            </a-button>
          </a-space>
          <span v-else class="text-muted">已处理</span>
        </template>
      </a-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import {
  IconSearch,
  IconRefresh,
  IconCheck,
  IconClose,
} from '@arco-design/web-vue/es/icon'

interface RefundRecord {
  id: number
  orderSn: string
  refundAmount: number
  reason: string
  status: number
  createTime: string
  updateTime: string
}

// Mock 退款数据
const mockRefunds: RefundRecord[] = [
  {
    id: 1,
    orderSn: 'ORD202401170003',
    refundAmount: 553,
    reason: '行程变更',
    status: 0,
    createTime: '2024-01-18 15:00:00',
    updateTime: '2024-01-18 15:00:00',
  },
  {
    id: 2,
    orderSn: 'ORD202401190004',
    refundAmount: 280,
    reason: '误购票',
    status: 1,
    createTime: '2024-01-20 10:30:00',
    updateTime: '2024-01-20 11:00:00',
  },
]

// 搜索表单
const searchForm = reactive({
  keyword: '',
  status: undefined as number | undefined,
})

// 表格数据
const loading = ref(false)
const tableData = ref<RefundRecord[]>([])
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

// 表格列定义
const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '订单号', dataIndex: 'orderSn', width: 180 },
  { title: '退款金额', dataIndex: 'refundAmount', width: 120 },
  { title: '退款原因', dataIndex: 'reason' },
  { title: '状态', slotName: 'status', width: 100 },
  { title: '申请时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', slotName: 'actions', width: 150 },
]

// 获取状态名称
const getStatusName = (status: number) => {
  const names = ['待处理', '已通过', '已拒绝', '已完成']
  return names[status] || '未知'
}

// 获取状态颜色
const getStatusColor = (status: number) => {
  const colors = ['orange', 'green', 'red', 'arcoblue']
  return colors[status] || 'gray'
}

// 获取退款列表
const fetchRefunds = async () => {
  loading.value = true
  try {
    await new Promise((resolve) => setTimeout(resolve, 500))

    let filteredData = [...mockRefunds]

    if (searchForm.keyword) {
      filteredData = filteredData.filter((r) =>
        r.orderSn.toLowerCase().includes(searchForm.keyword.toLowerCase())
      )
    }

    if (searchForm.status !== undefined) {
      filteredData = filteredData.filter((r) => r.status === searchForm.status)
    }

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
  fetchRefunds()
}

// 重置
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.status = undefined
  pagination.current = 1
  fetchRefunds()
}

// 分页变化
const handlePageChange = (page: number) => {
  pagination.current = page
  fetchRefunds()
}

// 审核
const handleAudit = (record: RefundRecord, approved: boolean) => {
  Modal.warning({
    title: approved ? '确认通过' : '确认拒绝',
    content: approved
      ? `确定通过订单 "${record.orderSn}" 的退款申请吗？退款金额 ¥${record.refundAmount}`
      : `确定拒绝订单 "${record.orderSn}" 的退款申请吗？`,
    okText: '确定',
    cancelText: '取消',
    hideCancel: false,
    onOk: () => {
      record.status = approved ? 1 : 2
      Message.success(approved ? '退款申请已通过' : '退款申请已拒绝')
    },
  })
}

onMounted(() => {
  fetchRefunds()
})
</script>

<style scoped lang="scss">
.text-muted {
  color: $text-muted;
}

:deep(.arco-table) {
  .arco-table-th {
    background: $bg-dark;
  }
}
</style>
