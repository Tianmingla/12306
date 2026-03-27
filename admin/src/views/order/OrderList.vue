// 订单管理页面

<template>
  <div class="order-list">
    <div class="page-header">
      <h1 class="page-title">订单管理</h1>
      <p class="page-subtitle">管理用户订单信息</p>
    </div>

    <!-- 搜索表单 -->
    <div class="search-form">
      <a-form :model="searchForm" layout="inline" auto-label-width>
        <a-form-item label="关键词">
          <a-input
            v-model="searchForm.keyword"
            placeholder="订单号/用户名/车次"
            allow-clear
            style="width: 200px"
          />
        </a-form-item>
        <a-form-item label="订单状态">
          <a-select v-model="searchForm.status" placeholder="全部" allow-clear style="width: 120px">
            <a-option :value="0">待支付</a-option>
            <a-option :value="1">已支付</a-option>
            <a-option :value="2">已取消</a-option>
            <a-option :value="3">已退款</a-option>
            <a-option :value="4">已完成</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="日期范围">
          <a-range-picker v-model="dateRange" style="width: 260px" />
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
            <a-button status="success" @click="handleExport">
              <template #icon><icon-download /></template>
              导出
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
        <template #orderSn="{ record }">
          <a-link @click="handleViewDetail(record)">{{ record.orderSn }}</a-link>
        </template>
        <template #trainInfo="{ record }">
          <div class="train-info">
            <span class="train-number">{{ record.trainNumber }}</span>
            <span class="route">{{ record.startStation }} → {{ record.endStation }}</span>
          </div>
        </template>
        <template #runDate="{ record }">
          <div>{{ record.runDate }}</div>
          <div class="time">{{ record.departureTime }} - {{ record.arrivalTime }}</div>
        </template>
        <template #amount="{ record }">
          <span class="amount">¥{{ record.totalAmount }}</span>
        </template>
        <template #status="{ record }">
          <a-tag :color="getStatusColor(record.status)">
            {{ getStatusName(record.status) }}
          </a-tag>
        </template>
        <template #actions="{ record }">
          <a-space>
            <a-button type="text" size="small" @click="handleViewDetail(record)">
              <template #icon><icon-eye /></template>
              详情
            </a-button>
            <a-button
              v-if="record.status === 1"
              type="text"
              size="small"
              status="warning"
              @click="handleRefund(record)"
            >
              <template #icon><icon-undo /></template>
              退款
            </a-button>
            <a-button
              v-if="record.status === 0"
              type="text"
              size="small"
              status="danger"
              @click="handleCancel(record)"
            >
              <template #icon><icon-close /></template>
              取消
            </a-button>
          </a-space>
        </template>
      </a-table>
    </div>

    <!-- 订单详情弹窗 -->
    <a-modal
      v-model:visible="detailVisible"
      title="订单详情"
      :footer="false"
      width="700px"
    >
      <a-descriptions :column="2" bordered>
        <a-descriptions-item label="订单号">{{ currentOrder?.orderSn }}</a-descriptions-item>
        <a-descriptions-item label="订单状态">
          <a-tag :color="getStatusColor(currentOrder?.status || 0)">
            {{ getStatusName(currentOrder?.status || 0) }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="用户名">{{ currentOrder?.username }}</a-descriptions-item>
        <a-descriptions-item label="乘车人">{{ currentOrder?.passengerName }}</a-descriptions-item>
        <a-descriptions-item label="车次">{{ currentOrder?.trainNumber }}</a-descriptions-item>
        <a-descriptions-item label="车厢/座位">
          {{ currentOrder?.carriageNumber }}车厢 {{ currentOrder?.seatNumber }}
        </a-descriptions-item>
        <a-descriptions-item label="出发站">{{ currentOrder?.startStation }}</a-descriptions-item>
        <a-descriptions-item label="到达站">{{ currentOrder?.endStation }}</a-descriptions-item>
        <a-descriptions-item label="出发日期">{{ currentOrder?.runDate }}</a-descriptions-item>
        <a-descriptions-item label="出发时间">{{ currentOrder?.departureTime }}</a-descriptions-item>
        <a-descriptions-item label="到达时间">{{ currentOrder?.arrivalTime }}</a-descriptions-item>
        <a-descriptions-item label="票价">
          <span class="amount">¥{{ currentOrder?.totalAmount }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="下单时间">{{ currentOrder?.createTime }}</a-descriptions-item>
        <a-descriptions-item label="支付时间">{{ currentOrder?.payTime || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import type { Order, OrderQueryParams } from '@/types'
import { mockOrders } from '@/mock/data'
import {
  IconSearch,
  IconRefresh,
  IconDownload,
  IconEye,
  IconUndo,
  IconClose,
} from '@arco-design/web-vue/es/icon'

// 搜索表单
const searchForm = reactive<OrderQueryParams>({
  keyword: '',
  status: undefined,
  pageNum: 1,
  pageSize: 10,
})

const dateRange = ref<string[]>([])

// 表格数据
const loading = ref(false)
const tableData = ref<Order[]>([])
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

// 表格列定义
const columns = [
  { title: '订单号', slotName: 'orderSn', width: 160 },
  { title: '用户名', dataIndex: 'username', width: 100 },
  { title: '车次信息', slotName: 'trainInfo', width: 200 },
  { title: '出发日期/时间', slotName: 'runDate', width: 160 },
  { title: '乘车人', dataIndex: 'passengerName', width: 100 },
  { title: '金额', slotName: 'amount', width: 100 },
  { title: '状态', slotName: 'status', width: 100 },
  { title: '操作', slotName: 'actions', width: 150 },
]

// 订单详情
const detailVisible = ref(false)
const currentOrder = ref<Order>()

// 获取状态名称
const getStatusName = (status: number) => {
  const names = ['待支付', '已支付', '已取消', '已退款', '已完成']
  return names[status] || '未知'
}

// 获取状态颜色
const getStatusColor = (status: number) => {
  const colors = ['orange', 'green', 'gray', 'red', 'arcoblue']
  return colors[status] || 'gray'
}

// 获取订单列表
const fetchOrders = async () => {
  loading.value = true
  try {
    await new Promise((resolve) => setTimeout(resolve, 500))

    let filteredData = [...mockOrders]

    if (searchForm.keyword) {
      const keyword = searchForm.keyword.toLowerCase()
      filteredData = filteredData.filter(
        (order) =>
          order.orderSn.toLowerCase().includes(keyword) ||
          order.username.toLowerCase().includes(keyword) ||
          order.trainNumber.toLowerCase().includes(keyword)
      )
    }

    if (searchForm.status !== undefined) {
      filteredData = filteredData.filter((order) => order.status === searchForm.status)
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
  fetchOrders()
}

// 重置
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.status = undefined
  dateRange.value = []
  pagination.current = 1
  fetchOrders()
}

// 分页变化
const handlePageChange = (page: number) => {
  pagination.current = page
  fetchOrders()
}

// 导出
const handleExport = () => {
  Message.success('订单导出成功')
}

// 查看详情
const handleViewDetail = (order: Order) => {
  currentOrder.value = order
  detailVisible.value = true
}

// 退款
const handleRefund = (order: Order) => {
  Modal.warning({
    title: '确认退款',
    content: `确定要为订单 "${order.orderSn}" 办理退款吗？退款金额 ¥${order.totalAmount}`,
    okText: '确定退款',
    cancelText: '取消',
    hideCancel: false,
    onOk: () => {
      order.status = 3
      Message.success('退款成功')
    },
  })
}

// 取消订单
const handleCancel = (order: Order) => {
  Modal.warning({
    title: '确认取消',
    content: `确定要取消订单 "${order.orderSn}" 吗？`,
    okText: '确定',
    cancelText: '取消',
    hideCancel: false,
    onOk: () => {
      order.status = 2
      Message.success('订单已取消')
    },
  })
}

onMounted(() => {
  fetchOrders()
})
</script>

<style scoped lang="scss">
.train-info {
  display: flex;
  flex-direction: column;

  .train-number {
    font-weight: 500;
    color: $primary-color;
  }

  .route {
    font-size: 12px;
    color: $text-muted;
  }
}

.time {
  font-size: 12px;
  color: $text-muted;
}

.amount {
  font-weight: 600;
  color: $primary-color;
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
