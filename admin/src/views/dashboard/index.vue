// Dashboard 首页

<template>
  <div class="dashboard">
    <div class="page-header">
      <h1 class="page-title">数据统计</h1>
      <p class="page-subtitle">实时监控铁路运营数据</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-content">
          <div class="stat-title">总用户数</div>
          <div class="stat-value">{{ stats.totalUsers.toLocaleString() }}</div>
          <div class="stat-footer">
            <span class="trend up">
              <icon-arrow-rise />
              {{ stats.userGrowth }}%
            </span>
            <span>较上月</span>
          </div>
        </div>
        <div class="stat-icon primary">
          <icon-user />
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-content">
          <div class="stat-title">总订单数</div>
          <div class="stat-value">{{ stats.totalOrders.toLocaleString() }}</div>
          <div class="stat-footer">
            <span class="trend up">
              <icon-arrow-rise />
              {{ stats.orderGrowth }}%
            </span>
            <span>较上月</span>
          </div>
        </div>
        <div class="stat-icon secondary">
          <icon-file />
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-content">
          <div class="stat-title">今日售票</div>
          <div class="stat-value">{{ stats.todayTickets.toLocaleString() }}</div>
          <div class="stat-footer">
            <span>张</span>
          </div>
        </div>
        <div class="stat-icon success">
          <icon-tickets />
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-content">
          <div class="stat-title">今日销售额</div>
          <div class="stat-value">¥{{ (stats.todayAmount / 10000).toFixed(1) }}万</div>
          <div class="stat-footer">
            <span>元</span>
          </div>
        </div>
        <div class="stat-icon warning">
          <icon-money-collect />
        </div>
      </div>
    </div>

    <!-- 图表区域 -->
    <div class="charts-grid">
      <div class="chart-card">
        <div class="chart-header">
          <h3>订单趋势</h3>
          <a-radio-group v-model="chartType" type="button" size="small">
            <a-radio value="week">近7天</a-radio>
            <a-radio value="month">近30天</a-radio>
          </a-radio-group>
        </div>
        <div ref="orderChartRef" class="chart-container"></div>
      </div>

      <div class="chart-card">
        <div class="chart-header">
          <h3>列车类型分布</h3>
        </div>
        <div ref="trainChartRef" class="chart-container"></div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="quick-actions">
      <div class="section-title">快捷操作</div>
      <div class="actions-grid">
        <div class="action-card" @click="router.push('/train/list')">
          <icon-train :size="32" />
          <span>车次管理</span>
        </div>
        <div class="action-card" @click="router.push('/order/list')">
          <icon-file :size="32" />
          <span>订单管理</span>
        </div>
        <div class="action-card" @click="router.push('/train/station')">
          <icon-location :size="32" />
          <span>站点管理</span>
        </div>
        <div class="action-card" @click="router.push('/system/user')">
          <icon-user :size="32" />
          <span>用户管理</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import type { DashboardStats, OrderTrend, TrainTypeDistribution } from '@/types'
import { getDashboardStats, getOrderTrend, getTrainTypeDistribution } from '@/api/stats'
import {
  IconUser,
  IconFile,
  IconArrowRise,
  IconLocation,
} from '@arco-design/web-vue/es/icon'

// 自定义图标
const IconTrain = {
  template: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C8 2 4 2.5 4 6v9.5c0 1.93 1.57 3.5 3.5 3.5L6 20.5v.5h2l1-2h6l1 2h2v-.5L16.5 19c1.93 0 3.5-1.57 3.5-3.5V6c0-3.5-4-4-8-4zm-1.5 14.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm7 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM18 10H6V6h12v4z"/></svg>`
}

const IconTickets = {
  template: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V6h16v12zM6 10h2v2H6zm0 4h8v2H6zm10 0h2v2h-2zm-6-4h8v2h-8z"/></svg>`
}

const router = useRouter()

// 统计数据
const stats = ref<DashboardStats>({
  totalUsers: 0,
  totalOrders: 0,
  totalTrains: 0,
  totalStations: 0,
  todayTickets: 0,
  todayAmount: 0,
  userGrowth: 0,
  orderGrowth: 0,
})

// 图表相关
const chartType = ref('week')
const orderChartRef = ref<HTMLElement>()
const trainChartRef = ref<HTMLElement>()
let orderChart: echarts.ECharts | null = null
let trainChart: echarts.ECharts | null = null

// 初始化订单趋势图表
const initOrderChart = () => {
  if (!orderChartRef.value) return

  orderChart = echarts.init(orderChartRef.value)

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#1E293B',
      borderColor: '#334155',
      textStyle: { color: '#F1F5F9' },
    },
    legend: {
      data: ['订单数', '销售额'],
      textStyle: { color: '#94A3B8' },
      top: 0,
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '15%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
      axisLine: { lineStyle: { color: '#334155' } },
      axisLabel: { color: '#94A3B8' },
    },
    yAxis: [
      {
        type: 'value',
        name: '订单数',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#94A3B8' },
        splitLine: { lineStyle: { color: '#334155' } },
      },
      {
        type: 'value',
        name: '销售额(万)',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#94A3B8' },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '订单数',
        type: 'line',
        smooth: true,
        data: [820, 932, 901, 934, 1290, 1330, 1320],
        itemStyle: { color: '#C41E3A' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(196, 30, 58, 0.3)' },
            { offset: 1, color: 'rgba(196, 30, 58, 0)' },
          ]),
        },
      },
      {
        name: '销售额',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: [45, 52, 48, 55, 68, 72, 70],
        itemStyle: { color: '#1E3A5F' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(30, 58, 95, 0.3)' },
            { offset: 1, color: 'rgba(30, 58, 95, 0)' },
          ]),
        },
      },
    ],
  }

  orderChart.setOption(option)
}

// 初始化列车类型分布图表
const initTrainChart = () => {
  if (!trainChartRef.value) return

  trainChart = echarts.init(trainChartRef.value)

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: '#1E293B',
      borderColor: '#334155',
      textStyle: { color: '#F1F5F9' },
    },
    legend: {
      orient: 'vertical',
      right: '5%',
      top: 'center',
      textStyle: { color: '#94A3B8' },
    },
    series: [
      {
        name: '列车类型',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        label: { show: false },
        emphasis: {
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 'bold',
            color: '#F1F5F9',
          },
        },
        labelLine: { show: false },
        data: [
          { value: 435, name: '高铁', itemStyle: { color: '#C41E3A' } },
          { value: 310, name: '动车', itemStyle: { color: '#1E3A5F' } },
          { value: 234, name: '特快', itemStyle: { color: '#10B981' } },
          { value: 135, name: '快速', itemStyle: { color: '#F59E0B' } },
        ],
      },
    ],
  }

  trainChart.setOption(option)
}

// 窗口大小变化时重绘图表
const handleResize = () => {
  orderChart?.resize()
  trainChart?.resize()
}

// 获取统计数据
const fetchDashboardStats = async () => {
  try {
    const res = await getDashboardStats()
    if (res.code === 200 || res.code === 0) {
      stats.value = res.data
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

// 获取订单趋势数据并更新图表
const fetchOrderTrend = async () => {
  try {
    const res = await getOrderTrend({ type: chartType.value as 'day' | 'week' | 'month' })
    if ((res.code === 200 || res.code === 0) && res.data && orderChart) {
      const data = res.data as any
      orderChart.setOption({
        xAxis: { data: data.dates || [] },
        series: [
          { data: data.orders || [] },
          { data: data.amounts || [] },
        ],
      })
    }
  } catch (error) {
    console.error('获取订单趋势失败:', error)
  }
}

// 获取列车分布数据并更新图表
const fetchTrainDistribution = async () => {
  try {
    const res = await getTrainTypeDistribution()
    if ((res.code === 200 || res.code === 0) && res.data && trainChart) {
      const distributionData = (res.data as TrainTypeDistribution[]).map(item => ({
        value: item.value,
        name: item.name,
        itemStyle: { color: getTrainColor(item.name) },
      }))
      trainChart.setOption({
        series: [{ data: distributionData }],
      })
    }
  } catch (error) {
    console.error('获取列车分布失败:', error)
  }
}

// 获取列车类型颜色
const getTrainColor = (name: string) => {
  const colorMap: Record<string, string> = {
    '高铁': '#C41E3A',
    '动车': '#1E3A5F',
    '特快': '#10B981',
    '快速': '#F59E0B',
  }
  return colorMap[name] || '#64748B'
}

onMounted(() => {
  // 获取统计数据
  fetchDashboardStats()

  // 初始化图表
  initOrderChart()
  initTrainChart()

  // 获取图表数据
  fetchOrderTrend()
  fetchTrainDistribution()

  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  orderChart?.dispose()
  trainChart?.dispose()
})
</script>

<style scoped lang="scss">
.dashboard {
  .stats-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 20px;
    margin-bottom: 24px;
  }

  .charts-grid {
    display: grid;
    grid-template-columns: 2fr 1fr;
    gap: 20px;
    margin-bottom: 24px;
  }
}

.chart-card {
  background: $bg-card;
  border-radius: $border-radius-lg;
  border: 1px solid $border-color;
  padding: 20px;

  .chart-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    h3 {
      font-size: 16px;
      font-weight: 500;
      color: $text-primary;
      margin: 0;
    }
  }

  .chart-container {
    height: 300px;
  }
}

.quick-actions {
  background: $bg-card;
  border-radius: $border-radius-lg;
  border: 1px solid $border-color;
  padding: 20px;

  .section-title {
    font-size: 16px;
    font-weight: 500;
    color: $text-primary;
    margin-bottom: 16px;
  }

  .actions-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
  }

  .action-card {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 24px;
    background: $bg-dark;
    border-radius: $border-radius;
    border: 1px solid $border-color;
    cursor: pointer;
    transition: all $transition;
    color: $text-secondary;

    &:hover {
      border-color: $primary-color;
      color: $primary-color;
      transform: translateY(-2px);
    }

    span {
      margin-top: 12px;
      font-size: 14px;
    }
  }
}

:deep(.arco-radio-button) {
  background: $bg-dark;
  border-color: $border-color;
  color: $text-secondary;

  &:hover {
    color: $primary-color;
  }

  &.arco-radio-checked {
    background: $primary-color;
    border-color: $primary-color;
    color: #fff;
  }
}

@media (max-width: 1200px) {
  .dashboard {
    .stats-grid {
      grid-template-columns: repeat(2, 1fr);
    }

    .charts-grid {
      grid-template-columns: 1fr;
    }

    .quick-actions .actions-grid {
      grid-template-columns: repeat(2, 1fr);
    }
  }
}

@media (max-width: 768px) {
  .dashboard {
    .stats-grid {
      grid-template-columns: 1fr;
    }

    .quick-actions .actions-grid {
      grid-template-columns: 1fr;
    }
  }
}
</style>
