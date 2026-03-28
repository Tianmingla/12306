// 操作日志页面

<template>
  <div class="operation-log">
    <div class="page-header">
      <h1 class="page-title">操作日志</h1>
      <p class="page-subtitle">查看系统操作记录</p>
    </div>

    <!-- 搜索表单 -->
    <div class="search-form">
      <a-form :model="searchForm" layout="inline" auto-label-width>
        <a-form-item label="操作人">
          <a-input
            v-model="searchForm.adminUsername"
            placeholder="请输入操作人"
            allow-clear
            style="width: 150px"
          />
        </a-form-item>
        <a-form-item label="操作类型">
          <a-select v-model="searchForm.operationType" placeholder="全部" allow-clear style="width: 120px">
            <a-option value="CREATE">新增</a-option>
            <a-option value="UPDATE">修改</a-option>
            <a-option value="DELETE">删除</a-option>
            <a-option value="LOGIN">登录</a-option>
            <a-option value="EXPORT">导出</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="操作模块">
          <a-input
            v-model="searchForm.module"
            placeholder="请输入模块"
            allow-clear
            style="width: 120px"
          />
        </a-form-item>
        <a-form-item label="操作状态">
          <a-select v-model="searchForm.status" placeholder="全部" allow-clear style="width: 100px">
            <a-option :value="0">成功</a-option>
            <a-option :value="1">失败</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="操作时间">
          <a-range-picker
            v-model="dateRange"
            style="width: 260px"
            @change="handleDateChange"
          />
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
        <template #operationType="{ record }">
          <a-tag :color="getOperationTypeColor(record.operationType)">
            {{ getOperationTypeName(record.operationType) }}
          </a-tag>
        </template>
        <template #status="{ record }">
          <a-tag :color="record.status === 0 ? 'green' : 'red'">
            {{ getStatusName(record.status) }}
          </a-tag>
        </template>
        <template #duration="{ record }">
          {{ record.duration }}ms
        </template>
        <template #actions="{ record }">
          <a-button type="text" size="small" @click="viewDetail(record)">
            详情
          </a-button>
        </template>
      </a-table>
    </div>

    <!-- 详情弹窗 -->
    <a-modal
      v-model:visible="detailVisible"
      title="操作日志详情"
      :footer="false"
      width="800px"
    >
      <div v-if="currentLog" class="log-detail">
        <a-descriptions :column="2" bordered>
          <a-descriptions-item label="日志ID">{{ currentLog.id }}</a-descriptions-item>
          <a-descriptions-item label="操作人">{{ currentLog.adminUsername }}</a-descriptions-item>
          <a-descriptions-item label="操作类型">
            <a-tag :color="getOperationTypeColor(currentLog.operationType)">
              {{ getOperationTypeName(currentLog.operationType) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="操作模块">{{ currentLog.module }}</a-descriptions-item>
          <a-descriptions-item label="请求方法">{{ currentLog.requestMethod }}</a-descriptions-item>
          <a-descriptions-item label="请求URL">{{ currentLog.requestUrl }}</a-descriptions-item>
          <a-descriptions-item label="IP地址">{{ currentLog.ip }}</a-descriptions-item>
          <a-descriptions-item label="执行时长">{{ currentLog.duration }}ms</a-descriptions-item>
          <a-descriptions-item label="操作状态">
            <a-tag :color="currentLog.status === 0 ? 'green' : 'red'">
              {{ getStatusName(currentLog.status) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="操作时间">{{ currentLog.createTime }}</a-descriptions-item>
          <a-descriptions-item label="操作描述" :span="2">{{ currentLog.description || '-' }}</a-descriptions-item>
        </a-descriptions>

        <div class="detail-section">
          <h4>请求参数</h4>
          <pre class="code-block">{{ formatJson(currentLog.requestParams) }}</pre>
        </div>

        <div class="detail-section">
          <h4>响应结果</h4>
          <pre class="code-block">{{ formatJson(currentLog.responseResult) }}</pre>
        </div>

        <div v-if="currentLog.errorMsg" class="detail-section error">
          <h4>错误信息</h4>
          <pre class="code-block">{{ currentLog.errorMsg }}</pre>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { getLogList, getLogDetail } from '@/api/log'
import type { OperationLog, LogQueryParams } from '@/types/log'
import { getOperationTypeName, getStatusName } from '@/types/log'
import {
  IconSearch,
  IconRefresh,
} from '@arco-design/web-vue/es/icon'

// 搜索表单
const searchForm = reactive<LogQueryParams>({
  pageNum: 1,
  pageSize: 10,
  adminUsername: '',
  operationType: '',
  module: '',
  status: undefined,
  startTime: '',
  endTime: '',
})

// 日期范围
const dateRange = ref<string[]>([])

// 表格数据
const loading = ref(false)
const tableData = ref<OperationLog[]>([])
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

// 表格列定义
const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '操作人', dataIndex: 'adminUsername', width: 100 },
  { title: '操作类型', slotName: 'operationType', width: 100 },
  { title: '操作模块', dataIndex: 'module', width: 100 },
  { title: '请求URL', dataIndex: 'requestUrl', ellipsis: true, tooltip: true },
  { title: 'IP地址', dataIndex: 'ip', width: 130 },
  { title: '执行时长', slotName: 'duration', width: 100 },
  { title: '状态', slotName: 'status', width: 80 },
  { title: '操作时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', slotName: 'actions', width: 80 },
]

// 详情弹窗
const detailVisible = ref(false)
const currentLog = ref<OperationLog | null>(null)

// 获取操作类型颜色
const getOperationTypeColor = (type: string) => {
  const colors: Record<string, string> = {
    'CREATE': 'green',
    'UPDATE': 'blue',
    'DELETE': 'red',
    'LOGIN': 'purple',
    'EXPORT': 'orange',
  }
  return colors[type] || 'gray'
}

// 格式化JSON
const formatJson = (jsonStr: string | undefined) => {
  if (!jsonStr) return '-'
  try {
    return JSON.stringify(JSON.parse(jsonStr), null, 2)
  } catch {
    return jsonStr
  }
}

// 日期变化
const handleDateChange = (val: string[]) => {
  if (val && val.length === 2) {
    searchForm.startTime = val[0]
    searchForm.endTime = val[1]
  } else {
    searchForm.startTime = ''
    searchForm.endTime = ''
  }
}

// 获取日志列表
const fetchLogs = async () => {
  loading.value = true
  try {
    const res = await getLogList(searchForm)
    if (res.code === 200 || res.code === 0) {
      tableData.value = res.data.list
      pagination.total = res.data.total
      pagination.current = res.data.pageNum || searchForm.pageNum
    } else {
      Message.error(res.message || '获取数据失败')
    }
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  searchForm.pageNum = 1
  pagination.current = 1
  fetchLogs()
}

// 重置
const handleReset = () => {
  searchForm.adminUsername = ''
  searchForm.operationType = ''
  searchForm.module = ''
  searchForm.status = undefined
  searchForm.startTime = ''
  searchForm.endTime = ''
  dateRange.value = []
  searchForm.pageNum = 1
  pagination.current = 1
  fetchLogs()
}

// 分页变化
const handlePageChange = (page: number) => {
  searchForm.pageNum = page
  pagination.current = page
  fetchLogs()
}

// 查看详情
const viewDetail = async (log: OperationLog) => {
  try {
    const res = await getLogDetail(log.id)
    if (res.code === 200 || res.code === 0) {
      currentLog.value = res.data
      detailVisible.value = true
    } else {
      Message.error(res.message || '获取详情失败')
    }
  } catch (error) {
    Message.error('获取详情失败')
  }
}

onMounted(() => {
  fetchLogs()
})
</script>

<style scoped lang="scss">
.operation-log {
  padding: 20px;
}

.search-form {
  background: $bg-card;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
}

.table-card {
  background: $bg-card;
  border-radius: 8px;
  overflow: hidden;
}

.log-detail {
  .detail-section {
    margin-top: 20px;

    h4 {
      margin-bottom: 8px;
      color: $text-primary;
    }

    &.error {
      h4 {
        color: $danger-color;
      }

      .code-block {
        border-color: $danger-color;
        background: rgba($danger-color, 0.1);
      }
    }
  }

  .code-block {
    background: $bg-dark;
    padding: 12px;
    border-radius: 4px;
    overflow-x: auto;
    font-family: monospace;
    font-size: 12px;
    line-height: 1.5;
    max-height: 200px;
    overflow-y: auto;
    white-space: pre-wrap;
    word-break: break-all;
  }
}

:deep(.arco-table) {
  .arco-table-th {
    background: $bg-dark;
  }
}
</style>
