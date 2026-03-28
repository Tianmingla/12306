// 线路管理页面

<template>
  <div class="route-manage">
    <div class="page-header">
      <h1 class="page-title">线路管理</h1>
      <p class="page-subtitle">管理列车运行线路和经停站</p>
    </div>

    <!-- 搜索表单 -->
    <div class="search-form">
      <a-form :model="searchForm" layout="inline" auto-label-width>
        <a-form-item label="车次号">
          <a-input
            v-model="searchForm.trainNumber"
            placeholder="请输入车次号"
            allow-clear
            style="width: 180px"
          />
        </a-form-item>
        <a-form-item label="车站名称">
          <a-input
            v-model="searchForm.stationName"
            placeholder="请输入车站名称"
            allow-clear
            style="width: 180px"
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
        row-key="trainId"
        @page-change="handlePageChange"
      >
        <template #trainType="{ record }">
          <a-tag :color="getTrainTypeColor(getTrainTypeFromNumber(record.trainNumber))">
            {{ getTrainTypeName(getTrainTypeFromNumber(record.trainNumber)) }}
          </a-tag>
        </template>
        <template #stations="{ record }">
          <a-tooltip :content="record.stations.map(s => s.stationName).join(' → ')">
            <span>{{ record.startStation }} → {{ record.endStation }}</span>
          </a-tooltip>
        </template>
        <template #duration="{ record }">
          {{ Math.floor(record.duration / 60) }}小时{{ record.duration % 60 }}分钟
        </template>
        <template #actions="{ record }">
          <a-space>
            <a-button type="text" size="small" @click="handleViewDetail(record)">
              <template #icon><icon-eye /></template>
              详情
            </a-button>
            <a-button type="text" size="small" @click="handleEditStations(record)">
              <template #icon><icon-edit /></template>
              编辑经停站
            </a-button>
          </a-space>
        </template>
      </a-table>
    </div>

    <!-- 线路详情弹窗 -->
    <a-modal
      v-model:visible="detailVisible"
      title="线路详情"
      :footer="false"
      width="900px"
    >
      <div v-if="currentRoute" class="route-detail">
        <a-descriptions :column="2" bordered>
          <a-descriptions-item label="车次号">{{ currentRoute.trainNumber }}</a-descriptions-item>
          <a-descriptions-item label="起点站">{{ currentRoute.startStation }}</a-descriptions-item>
          <a-descriptions-item label="终点站">{{ currentRoute.endStation }}</a-descriptions-item>
          <a-descriptions-item label="经停站数量">{{ currentRoute.stationCount }}个</a-descriptions-item>
          <a-descriptions-item label="出发时间">{{ currentRoute.departureTime }}</a-descriptions-item>
          <a-descriptions-item label="到达时间">{{ currentRoute.arrivalTime }}</a-descriptions-item>
          <a-descriptions-item label="运行时长">{{ Math.floor(currentRoute.duration / 60) }}小时{{ currentRoute.duration % 60 }}分钟</a-descriptions-item>
        </a-descriptions>

        <div class="station-list">
          <h3>经停站列表</h3>
          <a-table
            :columns="stationColumns"
            :data="currentRoute.stations"
            :pagination="false"
            size="small"
          >
            <template #sequence="{ record }">
              <a-tag color="arcoblue">{{ record.sequence }}</a-tag>
            </template>
            <template #arrivalTime="{ record }">
              {{ record.arrivalTime || '-' }}
            </template>
            <template #departureTime="{ record }">
              {{ record.departureTime || '-' }}
            </template>
            <template #stopoverTime="{ record }">
              {{ record.stopoverTime ? `${record.stopoverTime}分钟` : '-' }}
            </template>
          </a-table>
        </div>
      </div>
    </a-modal>

    <!-- 编辑经停站弹窗 -->
    <a-modal
      v-model:visible="editStationVisible"
      title="编辑经停站"
      :ok-loading="submitLoading"
      @ok="handleSaveStations"
      @cancel="handleCancelEdit"
    >
      <div v-if="currentTrain" class="edit-stations">
        <p>车次：<strong>{{ currentTrain.trainNumber }}</strong></p>

        <div class="station-input-list">
          <div v-for="(station, index) in stationList" :key="index" class="station-item">
            <a-row :gutter="8" align="center">
              <a-col :span="5">
                <a-input-number v-model="station.sequence" placeholder="序号" :min="1" style="width: 100%" />
              </a-col>
              <a-col :span="7">
                <a-input v-model="station.stationName" placeholder="车站名称" />
              </a-col>
              <a-col :span="5">
                <a-input v-model="station.arrivalTime" placeholder="到站时间 (HH:mm)" />
              </a-col>
              <a-col :span="5">
                <a-input v-model="station.departureTime" placeholder="出站时间 (HH:mm)" />
              </a-col>
              <a-col :span="2">
                <a-button type="text" status="danger" @click="removeStation(index)">
                  <icon-delete />
                </a-button>
              </a-col>
            </a-row>
          </div>
        </div>

        <a-button type="dashed" long @click="addStation">
          <template #icon><icon-plus /></template>
          添加经停站
        </a-button>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import type { TableColumnData } from '@arco-design/web-vue'
import { getRouteList, getRouteDetail, getTrainStations, batchSaveTrainStations } from '@/api/route'
import type {
  RouteQueryParams,
  RouteDetailResponse,
  TrainStationSaveRequest,
  StationVO,
} from '@/types/route'
import {
  IconSearch,
  IconRefresh,
  IconEye,
  IconEdit,
  IconPlus,
  IconDelete,
} from '@arco-design/web-vue/es/icon'

// 搜索表单
const searchForm = reactive<RouteQueryParams>({
  pageNum: 1,
  pageSize: 10,
  trainNumber: '',
  stationName: '',
})

// 表格数据
const loading = ref(false)
const tableData = ref<RouteDetailResponse[]>([])
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

// 表格列定义
const columns: TableColumnData[] = [
  { title: '车次号', dataIndex: 'trainNumber', width: 100 },
  { title: '列车类型', slotName: 'trainType', width: 100 },
  { title: '起点站', dataIndex: 'startStation', width: 120 },
  { title: '终点站', dataIndex: 'endStation', width: 120 },
  { title: '经停站', slotName: 'stations', width: 200 },
  { title: '出发时间', dataIndex: 'departureTime', width: 100 },
  { title: '到达时间', dataIndex: 'arrivalTime', width: 100 },
  { title: '运行时长', slotName: 'duration', width: 120 },
  { title: '操作', slotName: 'actions', width: 180 },
]

// 详情弹窗
const detailVisible = ref(false)
const currentRoute = ref<RouteDetailResponse | null>(null)

// 编辑经停站弹窗
const editStationVisible = ref(false)
const currentTrain = ref<RouteDetailResponse | null>(null)
const submitLoading = ref(false)
const stationList = ref<TrainStationSaveRequest[]>([])

// 站点表格列
const stationColumns: TableColumnData[] = [
  { title: '序号', slotName: 'sequence', width: 80 },
  { title: '车站名称', dataIndex: 'stationName' },
  { title: '到站时间', slotName: 'arrivalTime', width: 120 },
  { title: '出站时间', slotName: 'departureTime', width: 120 },
  { title: '停留时间', slotName: 'stopoverTime', width: 100 },
]

// 获取列车类型（简单判断）
const getTrainTypeFromNumber = (trainNumber: string): number => {
  if (!trainNumber) return 0
  const firstChar = trainNumber.charAt(0).toUpperCase()
  if (['G', 'C'].includes(firstChar)) return 0 // 高铁
  if (firstChar === 'D') return 1 // 动车
  if (['Z', 'T'].includes(firstChar)) return 2 // 特快
  if (firstChar === 'K') return 3 // 快速
  return 4 // 普快
}

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

// 获取线路列表
const fetchRoutes = async () => {
  loading.value = true
  try {
    const res = await getRouteList(searchForm)
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
  fetchRoutes()
}

// 重置
const handleReset = () => {
  searchForm.trainNumber = ''
  searchForm.stationName = ''
  searchForm.pageNum = 1
  pagination.current = 1
  fetchRoutes()
}

// 分页变化
const handlePageChange = (page: number) => {
  searchForm.pageNum = page
  pagination.current = page
  fetchRoutes()
}

// 查看详情
const handleViewDetail = async (record: RouteDetailResponse) => {
  try {
    const res = await getRouteDetail(record.trainId)
    if (res.code === 200 || res.code === 0) {
      currentRoute.value = res.data
      detailVisible.value = true
    } else {
      Message.error(res.message || '获取详情失败')
    }
  } catch (error) {
    Message.error('获取详情失败')
  }
}

// 编辑经停站
const handleEditStations = async (record: RouteDetailResponse) => {
  currentTrain.value = record

  // 加载现有经停站
  try {
    const res = await getTrainStations(record.trainId)
    if (res.code === 200 || res.code === 0) {
      stationList.value = res.data.map((station: StationVO) => ({
        trainId: record.trainId,
        trainNumber: record.trainNumber,
        stationId: station.id,
        stationName: station.stationName,
        sequence: station.sequence,
        arrivalTime: station.arrivalTime,
        departureTime: station.departureTime,
        stopoverTime: station.stopoverTime,
      }))
    } else {
      stationList.value = []
    }
  } catch (error) {
    stationList.value = []
  }

  editStationVisible.value = true
}

// 添加经停站
const addStation = () => {
  stationList.value.push({
    trainId: currentTrain.value?.trainId,
    trainNumber: currentTrain.value?.trainNumber,
    sequence: stationList.value.length + 1,
    stationName: '',
    arrivalTime: '',
    departureTime: '',
    stopoverTime: 0,
  })
}

// 删除经停站
const removeStation = (index: number) => {
  stationList.value.splice(index, 1)
  // 重新排序
  stationList.value.forEach((station, idx) => {
    station.sequence = idx + 1
  })
}

// 保存经停站
const handleSaveStations = async () => {
  if (!currentTrain.value) return

  submitLoading.value = true
  try {
    await batchSaveTrainStations(currentTrain.value.trainId!, stationList.value)
    Message.success('保存成功')
    editStationVisible.value = false
    fetchRoutes()
  } catch (error) {
    Message.error('保存失败')
  } finally {
    submitLoading.value = false
  }
}

// 取消编辑
const handleCancelEdit = () => {
  editStationVisible.value = false
  stationList.value = []
}

onMounted(() => {
  fetchRoutes()
})
</script>

<style scoped lang="scss">
.route-manage {
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

.route-detail {
  .station-list {
    margin-top: 24px;

    h3 {
      margin-bottom: 16px;
      font-size: 16px;
      color: $text-primary;
    }
  }
}

.edit-stations {
  .station-input-list {
    max-height: 400px;
    overflow-y: auto;
    margin-bottom: 16px;

    .station-item {
      margin-bottom: 12px;
      padding: 12px;
      background: $bg-dark;
      border-radius: 4px;
    }
  }
}

:deep(.arco-table) {
  .arco-table-th {
    background: $bg-dark;
  }
}
</style>
