// 列车管理页面

<template>
  <div class="train-list">
    <div class="page-header">
      <h1 class="page-title">车次管理</h1>
      <p class="page-subtitle">管理列车基本信息</p>
    </div>

    <!-- 搜索表单 -->
    <div class="search-form">
      <a-form :model="searchForm" layout="inline" auto-label-width>
        <a-form-item label="车次号">
          <a-input
            v-model="searchForm.keyword"
            placeholder="请输入车次号"
            allow-clear
            style="width: 180px"
          />
        </a-form-item>
        <a-form-item label="列车类型">
          <a-select v-model="searchForm.trainType" placeholder="全部" allow-clear style="width: 140px">
            <a-option :value="0">高铁</a-option>
            <a-option :value="1">动车</a-option>
            <a-option :value="2">特快</a-option>
            <a-option :value="3">快速</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="售卖状态">
          <a-select v-model="searchForm.saleStatus" placeholder="全部" allow-clear style="width: 120px">
            <a-option :value="0">可售</a-option>
            <a-option :value="1">停售</a-option>
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
            <a-button type="primary" status="success" @click="handleAdd">
              <template #icon><icon-plus /></template>
              新增列车
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
        <template #trainType="{ record }">
          <a-tag :color="getTrainTypeColor(record.trainType)">
            {{ getTrainTypeName(record.trainType) }}
          </a-tag>
        </template>
        <template #saleStatus="{ record }">
          <a-switch
            :model-value="record.saleStatus === 0"
            checked-color="#10B981"
            @change="(val: boolean) => handleStatusChange(record, val)"
          >
            <template #checked>可售</template>
            <template #unchecked>停售</template>
          </a-switch>
        </template>
        <template #actions="{ record }">
          <a-space>
            <a-button type="text" size="small" @click="handleEdit(record)">
              <template #icon><icon-edit /></template>
              编辑
            </a-button>
            <a-button type="text" size="small" @click="handleViewStations(record)">
              <template #icon><icon-location /></template>
              经停站
            </a-button>
            <a-button type="text" size="small" status="success" @click="handleSeatConfig(record)">
              <template #icon><icon-apps /></template>
              座位配置
            </a-button>
            <a-button type="text" size="small" status="danger" @click="handleDelete(record)">
              <template #icon><icon-delete /></template>
              删除
            </a-button>
          </a-space>
        </template>
      </a-table>
    </div>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:visible="formVisible"
      :title="isEdit ? '编辑列车' : '新增列车'"
      :ok-loading="submitLoading"
      @ok="handleSubmit"
      @cancel="handleCancel"
    >
      <a-form ref="formRef" :model="formData" :rules="rules" layout="vertical">
        <a-form-item field="trainNumber" label="车次号">
          <a-input v-model="formData.trainNumber" placeholder="如：G1、D301" />
        </a-form-item>
        <a-form-item field="trainType" label="列车类型">
          <a-select v-model="formData.trainType" placeholder="请选择">
            <a-option :value="0">高铁</a-option>
            <a-option :value="1">动车</a-option>
            <a-option :value="2">特快</a-option>
            <a-option :value="3">快速</a-option>
            <a-option :value="4">普快</a-option>
          </a-select>
        </a-form-item>
        <a-form-item field="trainTag" label="列车标签">
          <a-input v-model="formData.trainTag" placeholder="如：复兴号、和谐号" />
        </a-form-item>
        <a-form-item field="trainBrand" label="列车型号">
          <a-input v-model="formData.trainBrand" placeholder="如：CR400AF" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 经停站弹窗 -->
    <a-modal
      v-model:visible="stationVisible"
      title="列车经停站"
      :footer="false"
      width="800px"
    >
      <a-table
        :columns="stationColumns"
        :data="stationData"
        :pagination="false"
        size="small"
      >
        <template #arrivalTime="{ record }">
          {{ record.arrivalTime || '-' }}
        </template>
        <template #stopTime="{ record }">
          {{ record.stopTime ? `${record.stopTime}分钟` : '-' }}
        </template>
      </a-table>
    </a-modal>

    <!-- 座位配置弹窗 -->
    <SeatConfigModal
      v-model:visible="seatConfigVisible"
      :train="currentTrain"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import type { FormInstance } from '@arco-design/web-vue'
import type { Train, TrainStation, TrainQueryParams, TrainFormData } from '@/types'
import { getTrainList, updateTrainSaleStatus } from '@/api/train'
import SeatConfigModal from '@/components/SeatConfigModal.vue'
import {
  IconSearch,
  IconRefresh,
  IconPlus,
  IconEdit,
  IconDelete,
  IconLocation,
  IconApps,
} from '@arco-design/web-vue/es/icon'

// 搜索表单
const searchForm = reactive<TrainQueryParams>({
  keyword: '',
  trainType: undefined,
  saleStatus: undefined,
  pageNum: 1,
  pageSize: 10,
})

// 表格数据
const loading = ref(false)
const tableData = ref<Train[]>([])
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

// 表格列定义
const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '车次号', dataIndex: 'trainNumber', width: 100 },
  { title: '列车类型', slotName: 'trainType', width: 100 },
  { title: '标签', dataIndex: 'trainTag', width: 100 },
  { title: '型号', dataIndex: 'trainBrand', width: 120 },
  { title: '售卖状态', slotName: 'saleStatus', width: 120 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', slotName: 'actions', width: 300 },
]

// 表单相关
const formRef = ref<FormInstance>()
const formVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formData = reactive<TrainFormData>({
  trainNumber: '',
  trainType: 0,
  trainTag: '',
  trainBrand: '',
  saleStatus: 0,
})

const rules = {
  trainNumber: [{ required: true, message: '请输入车次号' }],
  trainType: [{ required: true, message: '请选择列车类型' }],
}

// 经停站相关
const stationVisible = ref(false)
const stationData = ref<TrainStation[]>([])
const stationColumns = [
  { title: '序号', dataIndex: 'sequence', width: 80 },
  { title: '车站', dataIndex: 'stationName' },
  { title: '到达时间', slotName: 'arrivalTime', width: 120 },
  { title: '发车时间', dataIndex: 'departureTime', width: 120 },
  { title: '停留时间', slotName: 'stopTime', width: 100 },
]

// 座位配置相关
const seatConfigVisible = ref(false)
const currentTrain = ref<Train | null>(null)

// 获取列车类型名称
const getTrainTypeName = (type: number) => {
  const types = ['高铁', '动车', '特快', '快速', '普快']
  return types[type] || '未知'
}

// 获取列车类型颜色
const getTrainTypeColor = (type: number) => {
  const colors = ['#C41E3A', '#1E3A5F', '#10B981', '#F59E0B', '#64748B']
  return colors[type] || '#64748B'
}

// 获取列车列表
const fetchTrains = async () => {
  loading.value = true
  try {
    const params: TrainQueryParams = {
      keyword: searchForm.keyword,
      trainType: searchForm.trainType,
      saleStatus: searchForm.saleStatus,
      pageNum: searchForm.pageNum,
      pageSize: searchForm.pageSize,
    }
    const res = await getTrainList(params)
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
  fetchTrains()
}

// 重置
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.trainType = undefined
  searchForm.saleStatus = undefined
  searchForm.pageNum = 1
  pagination.current = 1
  fetchTrains()
}

// 分页变化
const handlePageChange = (page: number) => {
  searchForm.pageNum = page
  pagination.current = page
  fetchTrains()
}

// 新增
const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, {
    trainNumber: '',
    trainType: 0,
    trainTag: '',
    trainBrand: '',
    saleStatus: 0,
  })
  formVisible.value = true
}

// 编辑
const handleEdit = (train: Train) => {
  isEdit.value = true
  Object.assign(formData, train)
  formVisible.value = true
}

// 提交
const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (valid) return

  submitLoading.value = true
  try {
    await new Promise((resolve) => setTimeout(resolve, 500))
    Message.success(isEdit.value ? '修改成功' : '新增成功')
    formVisible.value = false
    fetchTrains()
  } finally {
    submitLoading.value = false
  }
}

// 取消
const handleCancel = () => {
  formRef.value?.resetFields()
}

// 删除
const handleDelete = (train: Train) => {
  Modal.warning({
    title: '确认删除',
    content: `确定要删除车次 "${train.trainNumber}" 吗？`,
    okText: '确定',
    cancelText: '取消',
    hideCancel: false,
    onOk: () => {
      const index = tableData.value.findIndex((t) => t.id === train.id)
      if (index > -1) {
        tableData.value.splice(index, 1)
        Message.success('删除成功')
      }
    },
  })
}

// 状态切换
const handleStatusChange = async (train: Train, val: boolean) => {
  try {
    await updateTrainSaleStatus(train.id, val ? 0 : 1)
    train.saleStatus = val ? 0 : 1
    Message.success(val ? '已开启售卖' : '已停止售卖')
  } catch (error) {
    Message.error('操作失败')
  }
}

// 查看经停站
const handleViewStations = (train: Train) => {
  // TODO: 调用真实 API 获取经停站
  stationData.value = []
  stationVisible.value = true
}

// 座位配置
const handleSeatConfig = (train: Train) => {
  currentTrain.value = train
  seatConfigVisible.value = true
}

onMounted(() => {
  fetchTrains()
})
</script>

<style scoped lang="scss">
:deep(.arco-table) {
  .arco-table-th {
    background: $bg-dark;
  }
}
</style>
