import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/BasicLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据统计', icon: 'DataAnalysis' },
      },
      {
        path: 'train',
        name: 'Train',
        redirect: '/train/list',
        meta: { title: '车票管理', icon: 'Train' },
        children: [
          {
            path: 'list',
            name: 'TrainList',
            component: () => import('@/views/train/TrainList.vue'),
            meta: { title: '车次管理', icon: 'List' },
          },
          {
            path: 'station',
            name: 'StationManage',
            component: () => import('@/views/train/StationManage.vue'),
            meta: { title: '站点管理', icon: 'Location' },
          },
          {
            path: 'route',
            name: 'RouteManage',
            component: () => import('@/views/train/RouteManage.vue'),
            meta: { title: '线路管理', icon: 'MapLocation' },
          },
        ],
      },
      {
        path: 'order',
        name: 'Order',
        redirect: '/order/list',
        meta: { title: '订单管理', icon: 'Document' },
        children: [
          {
            path: 'list',
            name: 'OrderList',
            component: () => import('@/views/order/OrderList.vue'),
            meta: { title: '订单列表', icon: 'List' },
          },
          {
            path: 'refund',
            name: 'RefundManage',
            component: () => import('@/views/order/RefundManage.vue'),
            meta: { title: '退款管理', icon: 'RefreshLeft' },
          },
        ],
      },
      {
        path: 'system',
        name: 'System',
        redirect: '/system/user',
        meta: { title: '系统管理', icon: 'Setting' },
        children: [
          {
            path: 'user',
            name: 'UserManage',
            component: () => import('@/views/system/UserManage.vue'),
            meta: { title: '用户管理', icon: 'User' },
          },
          {
            path: 'role',
            name: 'RoleManage',
            component: () => import('@/views/system/RoleManage.vue'),
            meta: { title: '角色管理', icon: 'UserFilled' },
          },
          {
            path: 'log',
            name: 'OperationLog',
            component: () => import('@/views/system/OperationLog.vue'),
            meta: { title: '操作日志', icon: 'Document' },
          },
        ],
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach((to, from, next) => {
  // 设置页面标题
  document.title = `${to.meta.title || '管理后台'} - 铁路出行`

  const userStore = useUserStore()
  const token = userStore.token || localStorage.getItem('admin_token')

  if (to.path === '/login') {
    next()
  } else if (!token) {
    next('/login')
  } else {
    next()
  }
})

export default router
