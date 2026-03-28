// 角色管理页面

<template>
  <div class="role-manage">
    <div class="page-header">
      <h1 class="page-title">角色管理</h1>
      <p class="page-subtitle">管理系统角色权限</p>
    </div>

    <!-- 搜索表单 -->
    <div class="search-form">
      <a-form :model="searchForm" layout="inline" auto-label-width>
        <a-form-item label="角色名称">
          <a-input
            v-model="searchForm.roleName"
            placeholder="请输入角色名称"
            allow-clear
            style="width: 180px"
          />
        </a-form-item>
        <a-form-item label="角色编码">
          <a-input
            v-model="searchForm.roleCode"
            placeholder="请输入角色编码"
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
            <a-button type="primary" status="success" @click="handleAdd">
              <template #icon><icon-plus /></template>
              新增角色
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
            <a-button type="text" size="small" @click="handleEdit(record)">
              <template #icon><icon-edit /></template>
              编辑
            </a-button>
            <a-button type="text" size="small" @click="handleAssignPermissions(record)">
              <template #icon><icon-safe /></template>
              分配权限
            </a-button>
            <a-button type="text" size="small" @click="toggleStatus(record)">
              {{ record.status === 0 ? '禁用' : '启用' }}
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
      :title="isEdit ? '编辑角色' : '新增角色'"
      :ok-loading="submitLoading"
      @ok="handleSubmit"
      @cancel="handleCancel"
    >
      <a-form ref="formRef" :model="formData" :rules="rules" layout="vertical">
        <a-form-item field="roleName" label="角色名称">
          <a-input v-model="formData.roleName" placeholder="请输入角色名称" />
        </a-form-item>
        <a-form-item field="roleCode" label="角色编码">
          <a-input v-model="formData.roleCode" placeholder="请输入角色编码" />
        </a-form-item>
        <a-form-item field="description" label="角色描述">
          <a-textarea v-model="formData.description" placeholder="请输入角色描述" :max-length="200" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 分配权限弹窗 -->
    <a-modal
      v-model:visible="permissionVisible"
      title="分配权限"
      :ok-loading="permissionLoading"
      @ok="handleSavePermissions"
      @cancel="handleCancelPermission"
      width="600px"
    >
      <div class="permission-tree">
        <a-spin :loading="permissionLoading">
          <a-tree
            v-if="!permissionLoading"
            :data="permissionTree"
            :checked-keys="selectedPermissionIds"
            :checkable="true"
            :check-strictly="true"
            @check="handleCheck"
            :field-names="{ key: 'id', title: 'permissionName' }"
          >
            <template #title="node">
              <span>{{ node.permissionName }}</span>
              <span class="permission-code">({{ node.permissionCode }})</span>
              <a-tag v-if="node.resourceType" size="small" style="margin-left: 8px;">
                {{ getResourceTypeName(node.resourceType) }}
              </a-tag>
            </template>
          </a-tree>
        </a-spin>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import type { FormInstance } from '@arco-design/web-vue'
import type { TreeNodeData } from '@arco-design/web-vue'
import { getRoleList, createRole, updateRole, deleteRole, updateRoleStatus, getAllPermissions, getRolePermissionIds, assignPermissions } from '@/api/role'
import type { Role, Permission, RoleSaveRequest, RoleQueryParams } from '@/types/role'
import { getResourceTypeName } from '@/types/role'
import {
  IconSearch,
  IconRefresh,
  IconPlus,
  IconEdit,
  IconDelete,
  IconSafe,
} from '@arco-design/web-vue/es/icon'

// 搜索表单
const searchForm = reactive<RoleQueryParams>({
  pageNum: 1,
  pageSize: 10,
  roleName: '',
  roleCode: '',
})

// 表格数据
const loading = ref(false)
const tableData = ref<Role[]>([])
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

// 表格列定义
const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '角色名称', dataIndex: 'roleName', width: 150 },
  { title: '角色编码', dataIndex: 'roleCode', width: 150 },
  { title: '描述', dataIndex: 'description', ellipsis: true, tooltip: true },
  { title: '状态', slotName: 'status', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', slotName: 'actions', width: 300 },
]

// 表单相关
const formRef = ref<FormInstance>()
const formVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formData = reactive<RoleSaveRequest>({
  roleName: '',
  roleCode: '',
  description: '',
})

const rules = {
  roleName: [{ required: true, message: '请输入角色名称' }],
  roleCode: [{ required: true, message: '请输入角色编码' }],
}

// 权限相关
const permissionVisible = ref(false)
const permissionLoading = ref(false)
const currentRole = ref<Role | null>(null)
const allPermissions = ref<Permission[]>([])
const permissionTree = ref<TreeNodeData[]>([])
const selectedPermissionIds = ref<number[]>([])

// 获取角色列表
const fetchRoles = async () => {
  loading.value = true
  try {
    const res = await getRoleList(searchForm)
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
  fetchRoles()
}

// 重置
const handleReset = () => {
  searchForm.roleName = ''
  searchForm.roleCode = ''
  searchForm.pageNum = 1
  pagination.current = 1
  fetchRoles()
}

// 分页变化
const handlePageChange = (page: number) => {
  searchForm.pageNum = page
  pagination.current = page
  fetchRoles()
}

// 新增
const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, {
    roleName: '',
    roleCode: '',
    description: '',
  })
  formVisible.value = true
}

// 编辑
const handleEdit = (role: Role) => {
  isEdit.value = true
  Object.assign(formData, {
    id: role.id,
    roleName: role.roleName,
    roleCode: role.roleCode,
    description: role.description,
  })
  formVisible.value = true
}

// 提交
const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (valid) return

  submitLoading.value = true
  try {
    if (isEdit.value && formData.id) {
      await updateRole(formData.id, formData)
      Message.success('修改成功')
    } else {
      await createRole(formData)
      Message.success('创建成功')
    }
    formVisible.value = false
    fetchRoles()
  } catch (error: any) {
    Message.error(error.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

// 取消
const handleCancel = () => {
  formRef.value?.resetFields()
}

// 删除
const handleDelete = (role: Role) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除角色 "${role.roleName}" 吗？`,
    onOk: async () => {
      try {
        await deleteRole(role.id)
        Message.success('删除成功')
        fetchRoles()
      } catch (error: any) {
        Message.error(error.message || '删除失败')
      }
    }
  })
}

// 切换状态
const toggleStatus = async (role: Role) => {
  try {
    const newStatus = role.status === 0 ? 1 : 0
    await updateRoleStatus(role.id, newStatus)
    role.status = newStatus
    Message.success(newStatus === 0 ? '已启用' : '已禁用')
  } catch (error: any) {
    Message.error('操作失败')
  }
}

// 分配权限
const handleAssignPermissions = async (role: Role) => {
  currentRole.value = role
  permissionVisible.value = true
  permissionLoading.value = true

  try {
    // 获取所有权限
    const permRes = await getAllPermissions()
    if (permRes.code === 200 || permRes.code === 0) {
      allPermissions.value = permRes.data || []
      // 构建权限树
      permissionTree.value = buildPermissionTree(allPermissions.value)
    }

    // 获取角色已有的权限
    const rolePermRes = await getRolePermissionIds(role.id)
    if (rolePermRes.code === 200 || rolePermRes.code === 0) {
      selectedPermissionIds.value = rolePermRes.data || []
    }
  } catch (error) {
    Message.error('获取权限数据失败')
  } finally {
    permissionLoading.value = false
  }
}

// 构建权限树
const buildPermissionTree = (permissions: Permission[]): TreeNodeData[] => {
  const tree: TreeNodeData[] = []
  const map = new Map<number, TreeNodeData>()

  // 先创建所有节点
  permissions.forEach(p => {
    const node: TreeNodeData = {
      id: p.id,
      permissionName: p.permissionName,
      permissionCode: p.permissionCode,
      resourceType: p.resourceType,
      parentId: p.parentId,
    }
    map.set(p.id, node)
  })

  // 构建树结构
  permissions.forEach(p => {
    const node = map.get(p.id)
    if (p.parentId === 0) {
      tree.push(node!)
    } else {
      const parent = map.get(p.parentId)
      if (parent) {
        if (!parent.children) parent.children = []
        parent.children.push(node!)
      }
    }
  })

  return tree
}

// 权限勾选
const handleCheck = (checkedKeys: number[]) => {
  selectedPermissionIds.value = checkedKeys
}

// 保存权限
const handleSavePermissions = async () => {
  if (!currentRole.value) return

  permissionLoading.value = true
  try {
    await assignPermissions(currentRole.value.id, selectedPermissionIds.value)
    Message.success('权限分配成功')
    permissionVisible.value = false
  } catch (error: any) {
    Message.error(error.message || '权限分配失败')
  } finally {
    permissionLoading.value = false
  }
}

// 取消权限分配
const handleCancelPermission = () => {
  permissionVisible.value = false
  currentRole.value = null
  selectedPermissionIds.value = []
}

onMounted(() => {
  fetchRoles()
})
</script>

<style scoped lang="scss">
.role-manage {
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

.permission-tree {
  max-height: 400px;
  overflow-y: auto;

  .permission-code {
    color: $text-muted;
    font-size: 12px;
    margin-left: 8px;
  }
}

:deep(.arco-table) {
  .arco-table-th {
    background: $bg-dark;
  }
}
</style>
