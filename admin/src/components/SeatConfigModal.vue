<template>
  <a-modal
    v-model:visible="visible"
    title="座位配置"
    :ok-loading="submitLoading"
    @ok="handleSave"
    @cancel="handleCancel"
    width="900px"
  >
    <div class="seat-config">
      <div class="train-info">
        <h3>车次：{{ train?.trainNumber }}</h3>
      </div>

      <!-- 车厢管理 -->
      <div class="carriage-section">
        <div class="section-header">
          <h4>车厢管理</h4>
          <a-button type="primary" size="small" @click="showAddCarriage = true">
            <template #icon><icon-plus /></template>
            添加车厢
          </a-button>
        </div>

        <a-table
          :columns="carriageColumns"
          :data="carriages"
          :pagination="false"
          size="small"
          row-key="id"
        >
          <template #carriageType="{ record }">
            <a-tag color="blue">{{ getCarriageTypeName(record.carriageType) }}</a-tag>
          </template>
          <template #actions="{ record }">
            <a-space>
              <a-button type="text" size="mini" @click="viewSeats(record)">
                查看座位
              </a-button>
              <a-button type="text" size="mini" @click="editCarriage(record)">
                编辑
              </a-button>
              <a-button type="text" size="mini" status="danger" @click="deleteCarriage(record)">
                删除
              </a-button>
            </a-space>
          </template>
        </a-table>
      </div>

      <!-- 座位预览 -->
      <div v-if="selectedCarriage" class="seat-preview-section">
        <div class="section-header">
          <h4>{{ selectedCarriage.carriageNumber }}号车厢座位布局</h4>
          <a-button @click="selectedCarriage = null">关闭预览</a-button>
        </div>
        <div class="seat-layout">
          <div class="carriage-label">
            {{ selectedCarriage.carriageNumber }}号车厢 ({{ getCarriageTypeName(selectedCarriage.carriageType) }})
          </div>
          <div class="seat-grid">
            <div v-for="seat in seats" :key="seat.id" class="seat-item">
              <a-popover :content="getSeatTooltip(seat)">
                <div
                  class="seat"
                  :class="getSeatClass(seat)"
                  @click="editSeatType(seat)"
                >
                  {{ seat.seatNumber }}
                </div>
              </a-popover>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 添加/编辑车厢弹窗 -->
    <a-modal
      v-model:visible="showAddCarriage"
      :title="editingCarriage ? '编辑车厢' : '添加车厢'"
      @ok="handleSaveCarriage"
      @cancel="cancelCarriageEdit"
      width="400px"
    >
      <a-form :model="carriageForm" layout="vertical">
        <a-form-item field="carriageNumber" label="车厢号">
          <a-input v-model="carriageForm.carriageNumber" placeholder="如：1、2、3" />
        </a-form-item>
        <a-form-item field="carriageType" label="车厢类型">
          <a-select v-model="carriageForm.carriageType" placeholder="请选择">
            <a-option :value="0">商务座</a-option>
            <a-option :value="1">一等座</a-option>
            <a-option :value="2">二等座</a-option>
            <a-option :value="3">硬卧</a-option>
            <a-option :value="4">软卧</a-option>
            <a-option :value="5">硬座</a-option>
            <a-option :value="6">软座</a-option>
          </a-select>
        </a-form-item>
        <a-form-item field="seatCount" label="座位数">
          <a-input-number v-model="carriageForm.seatCount" :min="1" :max="100" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 编辑座位类型弹窗 -->
    <a-modal
      v-model:visible="showEditSeat"
      title="修改座位类型"
      @ok="handleSaveSeatType"
      @cancel="cancelSeatEdit"
      width="300px"
    >
      <a-form :model="seatForm" layout="vertical">
        <a-form-item field="seatType" label="座位类型">
          <a-select v-model="seatForm.seatType" placeholder="请选择">
            <a-option :value="0">靠窗</a-option>
            <a-option :value="1">靠过道</a-option>
            <a-option :value="2">中间</a-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import type { TableColumnData } from '@arco-design/web-vue'
import { getTrainCarriages, addCarriage, updateCarriage, deleteCarriage, getCarriageSeats, updateSeatType } from '@/api/train'
import type { Carriage, Seat, CarriageSaveRequest } from '@/types/train'
import { getCarriageTypeName } from '@/types/train'
import { IconPlus } from '@arco-design/web-vue/es/icon'

// Props
const props = defineProps<{
  visible: boolean
  train: any
}>()

// Emits
const emit = defineEmits(['update:visible'])

// 响应式数据
const submitLoading = ref(false)
const carriages = ref<Carriage[]>([])
const seats = ref<Seat[]>([])
const selectedCarriage = ref<Carriage | null>(null)
const showAddCarriage = ref(false)
const editingCarriage = ref<Carriage | null>(null)
const showEditSeat = ref(false)

// 车厢表单
const carriageForm = reactive<CarriageSaveRequest>({
  trainId: 0,
  carriageNumber: '',
  carriageType: 2,
  seatCount: 100
})

// 座位表单
const seatForm = reactive({
  seatType: 0
})

const editingSeat = ref<Seat | null>(null)

// 车厢表格列
const carriageColumns: TableColumnData[] = [
  { title: '车厢号', dataIndex: 'carriageNumber', width: 100 },
  { title: '类型', slotName: 'carriageType', width: 120 },
  { title: '座位数', dataIndex: 'seatCount', width: 100 },
  { title: '操作', slotName: 'actions', width: 200 },
]

// 监听弹窗显示
watch(() => props.visible, async (val) => {
  if (val && props.train) {
    await loadCarriages()
  }
})

// 加载车厢列表
const loadCarriages = async () => {
  try {
    const res = await getTrainCarriages(props.train.id)
    if (res.code === 200 || res.code === 0) {
      carriages.value = res.data || []
    } else {
      Message.error(res.message || '获取车厢列表失败')
    }
  } catch (error) {
    Message.error('获取车厢列表失败')
  }
}

// 查看座位
const viewSeats = async (carriage: Carriage) => {
  selectedCarriage.value = carriage
  try {
    const res = await getCarriageSeats(props.train.id, carriage.carriageNumber)
    if (res.code === 200 || res.code === 0) {
      seats.value = res.data || []
    } else {
      Message.error(res.message || '获取座位列表失败')
    }
  } catch (error) {
    Message.error('获取座位列表失败')
  }
}

// 添加车厢
const showAddCarriageDialog = () => {
  editingCarriage.value = null
  Object.assign(carriageForm, {
    trainId: props.train.id,
    carriageNumber: '',
    carriageType: 2,
    seatCount: 100
  })
  showAddCarriage.value = true
}

// 编辑车厢
const editCarriage = (carriage: Carriage) => {
  editingCarriage.value = carriage
  Object.assign(carriageForm, {
    trainId: props.train.id,
    carriageNumber: carriage.carriageNumber,
    carriageType: carriage.carriageType,
    seatCount: carriage.seatCount
  })
  showAddCarriage.value = true
}

// 保存车厢
const handleSaveCarriage = async () => {
  if (!carriageForm.carriageNumber) {
    Message.error('请输入车厢号')
    return
  }

  try {
    if (editingCarriage.value) {
      await updateCarriage(editingCarriage.value.id, carriageForm)
      Message.success('修改成功')
    } else {
      await addCarriage(carriageForm)
      Message.success('添加成功')
    }
    showAddCarriage.value = false
    await loadCarriages()
  } catch (error: any) {
    Message.error(error.message || '操作失败')
  }
}

// 取消车厢编辑
const cancelCarriageEdit = () => {
  showAddCarriage.value = false
  editingCarriage.value = null
}

// 删除车厢
const deleteCarriage = (carriage: Carriage) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除 ${carriage.carriageNumber} 号车厢吗？`,
    onOk: async () => {
      try {
        await deleteCarriage(carriage.id)
        Message.success('删除成功')
        await loadCarriages()
        if (selectedCarriage.value?.id === carriage.id) {
          selectedCarriage.value = null
        }
      } catch (error: any) {
        Message.error(error.message || '删除失败')
      }
    }
  })
}

// 编辑座位类型
const editSeatType = (seat: Seat) => {
  editingSeat.value = seat
  seatForm.seatType = seat.seatType
  showEditSeat.value = true
}

// 保存座位类型
const handleSaveSeatType = async () => {
  if (!editingSeat.value) return

  try {
    await updateSeatType(editingSeat.value.id, seatForm.seatType)
    Message.success('修改成功')
    showEditSeat.value = false
    // 刷新座位列表
    if (selectedCarriage.value) {
      await viewSeats(selectedCarriage.value)
    }
  } catch (error: any) {
    Message.error(error.message || '修改失败')
  }
}

// 取消座位编辑
const cancelSeatEdit = () => {
  showEditSeat.value = false
  editingSeat.value = null
}

// 获取座位CSS类
const getSeatClass = (seat: Seat) => {
  const classes = ['seat']
  if (seat.seatType === 0) classes.push('window')
  else if (seat.seatType === 1) classes.push('aisle')
  else classes.push('middle')
  return classes.join(' ')
}

// 获取座位提示信息
const getSeatTooltip = (seat: Seat) => {
  const typeName = seat.seatType === 0 ? '靠窗' : seat.seatType === 1 ? '靠过道' : '中间'
  return `${seat.seatNumber} - ${typeName}`
}

// 保存配置
const handleSave = async () => {
  submitLoading.value = true
  try {
    // 这里可以添加保存逻辑
    await new Promise(resolve => setTimeout(resolve, 500))
    Message.success('保存成功')
    emit('update:visible', false)
  } finally {
    submitLoading.value = false
  }
}

// 取消
const handleCancel = () => {
  emit('update:visible', false)
}

// 暴露方法给父组件
defineExpose({
  showAddCarriage: showAddCarriageDialog
})
</script>

<style scoped lang="scss">
.seat-config {
  .train-info {
    margin-bottom: 20px;
    padding: 12px;
    background: $bg-dark;
    border-radius: 4px;

    h3 {
      margin: 0;
      color: $text-primary;
    }
  }

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin: 20px 0 12px;

    h4 {
      margin: 0;
      color: $text-primary;
    }
  }

  .carriage-section {
    margin-bottom: 24px;
  }

  .seat-preview-section {
    border-top: 1px solid $border-color;
    padding-top: 20px;
  }

  .seat-layout {
    .carriage-label {
      font-weight: bold;
      margin-bottom: 12px;
      color: $text-primary;
    }

    .seat-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(60px, 1fr));
      gap: 8px;
      padding: 16px;
      background: $bg-dark;
      border-radius: 4px;
    }

    .seat-item {
      .seat {
        height: 40px;
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: 4px;
        cursor: pointer;
        font-size: 12px;
        font-weight: bold;
        transition: all 0.2s;

        &.window {
          background: #10B981;
          color: white;
        }

        &.aisle {
          background: #F59E0B;
          color: white;
        }

        &.middle {
          background: #64748B;
          color: white;
        }

        &:hover {
          transform: scale(1.1);
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
        }
      }
    }
  }
}
</style>
