// 站点管理页面

<template>
  <div class="station-manage">
    <div class="page-header">
      <h1 class="page-title">站点管理</h1>
      <p class="page-subtitle">管理车站基本信息</p>
    </div>

    <!-- 搜索表单 -->
    <div class="search-form">
      <a-form :model="searchForm" layout="inline" auto-label-width>
        <a-form-item label="关键词">
          <a-input
            v-model="searchForm.keyword"
            placeholder="车站名称/代码/拼音"
            allow-clear
            style="width: 200px"
          />
        </a-form-item>
        <a-form-item label="地区">
          <a-select v-model="searchForm.region" placeholder="全部" allow-clear style="width: 120px">
            <a-option value="HB">华北</a-option>
            <a-option value="HD">华东</a-option>
            <a-option value="HN">华南</a-option>
            <a-option value="HZ">华中</a-option>
            <a-option value="XN">西南</a-option>
            <a-option value="XB">西北</a-option>
            <a-option value="DB">东北</a-option>
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
              新增车站
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
        <template #regionName="{ record }">
          <a-tag>{{ record.regionName || '-' }}</a-tag>
        </template>
        <template #actions="{ record }">
          <a-space>
            <a-button type="text" size="small" @click="handleEdit(record)">
              <template #icon><icon-edit /></template>
              编辑
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
      :title="isEdit ? '编辑车站' : '新增车站'"
      :ok-loading="submitLoading"
      @ok="handleSubmit"
      @cancel="handleCancel"
    >
      <a-form ref="formRef" :model="formData" :rules="rules" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item field="code" label="车站代码">
              <a-input v-model="formData.code" placeholder="如：BJP" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item field="name" label="车站名称">
              <a-input v-model="formData.name" placeholder="如：北京" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item field="spell" label="拼音">
              <a-input v-model="formData.spell" placeholder="如：beijing" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item field="region" label="所属地区">
              <a-select v-model="formData.region" placeholder="请选择">
                <a-option value="HB">
                  <span>华北</span>
                </a-option>
                <a-option value="HD">
                  <span>华东</span>
                </a-option>
                <a-option value="HN">
                  <span>华南</span>
                </a-option>
                <a-option value="HZ">
                  <span>华中</span>
                </a-option>
                <a-option value="XN">
                  <span>西南</span>
                </a-option>
                <a-option value="XB">
                  <span>西北</span>
                </a-option>
                <a-option value="DB">
                  <span>东北</span>
                </a-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import type { FormInstance } from '@arco-design/web-vue'
import type { Station, StationQueryParams, StationFormData } from '@/types'
import { mockStations } from '@/mock/data'
import {
  IconSearch,
  IconRefresh,
  IconPlus,
  IconEdit,
  IconDelete,
} from '@arco-design/web-vue/es/icon'

// 搜索表单
const searchForm = reactive<StationQueryParams>({
  keyword: '',
  region: undefined,
  pageNum: 1,
  pageSize: 10,
})

// 表格数据
const loading = ref(false)
const tableData = ref<Station[]>([])
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

// 表格列定义
const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '车站代码', dataIndex: 'code', width: 100 },
  { title: '车站名称', dataIndex: 'name' },
  { title: '拼音', dataIndex: 'spell' },
  { title: '所属地区', slotName: 'regionName', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', slotName: 'actions', width: 150 },
]

// 表单相关
const formRef = ref<FormInstance>()
const formVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formData = reactive<StationFormData>({
  code: '',
  name: '',
  spell: '',
  region: '',
  regionName: '',
})

const rules = {
  code: [{ required: true, message: '请输入车站代码' }],
  name: [{ required: true, message: '请输入车站名称' }],
  spell: [{ required: true, message: '请输入拼音' }],
}

// 地区映射
const regionMap: Record<string, string> = {
  HB: '华北',
  HD: '华东',
  HN: '华南',
  HZ: '华中',
  XN: '西南',
  XB: '西北',
  DB: '东北',
}

// 获取车站列表
const fetchStations = async () => {
  loading.value = true
  try {
    await new Promise((resolve) => setTimeout(resolve, 500))

    let filteredData = [...mockStations]

    if (searchForm.keyword) {
      const keyword = searchForm.keyword.toLowerCase()
      filteredData = filteredData.filter(
        (station) =>
          station.name.includes(searchForm.keyword!) ||
          station.code.toLowerCase().includes(keyword) ||
          station.spell.includes(keyword)
      )
    }

    if (searchForm.region) {
      filteredData = filteredData.filter((station) => station.region === searchForm.region)
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
  fetchStations()
}

// 重置
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.region = undefined
  pagination.current = 1
  fetchStations()
}

// 分页变化
const handlePageChange = (page: number) => {
  pagination.current = page
  fetchStations()
}

// 新增
const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, {
    code: '',
    name: '',
    spell: '',
    region: '',
    regionName: '',
  })
  formVisible.value = true
}

// 编辑
const handleEdit = (station: Station) => {
  isEdit.value = true
  Object.assign(formData, station)
  formVisible.value = true
}

// 提交
const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (valid) return

  submitLoading.value = true
  try {
    await new Promise((resolve) => setTimeout(resolve, 500))

    // 更新地区名称
    if (formData.region) {
      formData.regionName = regionMap[formData.region] || ''
    }

    Message.success(isEdit.value ? '修改成功' : '新增成功')
    formVisible.value = false
    fetchStations()
  } finally {
    submitLoading.value = false
  }
}

// 取消
const handleCancel = () => {
  formRef.value?.resetFields()
}

// 删除
const handleDelete = (station: Station) => {
  Modal.warning({
    title: '确认删除',
    content: `确定要删除车站 "${station.name}" 吗？`,
    okText: '确定',
    cancelText: '取消',
    hideCancel: false,
    onOk: () => {
      const index = tableData.value.findIndex((s) => s.id === station.id)
      if (index > -1) {
        tableData.value.splice(index, 1)
        Message.success('删除成功')
      }
    },
  })
}

onMounted(() => {
  fetchStations()
})
</script>

<style scoped lang="scss">
:deep(.arco-table) {
  .arco-table-th {
    background: $bg-dark;
  }
}
</style>
