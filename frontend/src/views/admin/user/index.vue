<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import type { FormRules } from 'element-plus';
import type BaseForm from '@/components/BaseForm/index.vue';
import {
  changeAdminStatusApi,
  createAdminUserApi,
  deleteAdminUserApi,
  getAdminUserPageApi,
  getRoleOptionsApi,
  resetAdminPasswordApi,
  updateAdminUserApi,
} from '@/api';
import { useTable } from '@/hooks/useTable';
import { maskPhone } from '@/utils';
import type { AdminUserInfo, FormField, RoleItem, TableColumn } from '@/types';
import type { AdminUserQueryParams, AdminUserSaveParams } from '@/types/api';

/**
 * 管理员账号管理(sys_user)
 * 演示 BaseSearch + BaseTable + BaseDialog + BaseForm 组合用法
 */
const { loading, list, total, query, pagination, loadData, search, reset, handleCurrentChange, handleSizeChange } =
  useTable<AdminUserInfo, AdminUserQueryParams>({
    api: getAdminUserPageApi,
    defaultQuery: { keyword: '', roleId: undefined, status: undefined },
  });

/** 角色下拉数据 */
const roles = ref<RoleItem[]>([]);
const roleOptions = computed(() => roles.value.map((item) => ({ label: item.roleName, value: item.id })));

/** 查询条件 */
const searchFields = computed<FormField[]>(() => [
  { prop: 'keyword', label: '关键字', placeholder: '账号 / 姓名 / 手机号' },
  { prop: 'roleId', label: '角色', type: 'select', options: roleOptions.value },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '启用', value: 1 },
      { label: '禁用', value: 0 },
    ],
  },
]);

/** 表格列 */
const columns: TableColumn<AdminUserInfo>[] = [
  { prop: 'id', label: 'ID', width: 60 },
  { prop: 'username', label: '登录账号', minWidth: 110 },
  { prop: 'realName', label: '姓名', minWidth: 100 },
  {
    prop: 'phone',
    label: '手机号',
    width: 120,
    formatter: (row: AdminUserInfo) => maskPhone(row.phone),
  },
  { prop: 'roleName', label: '角色', width: 120, slot: 'role' },
  { prop: 'status', label: '状态', width: 80, slot: 'status' },
  { prop: 'lastLoginTime', label: '最后登录', width: 150 },
  { prop: 'createTime', label: '创建时间', width: 150 },
  { label: '操作', width: 190, slot: 'action', fixed: 'right' },
];

/** 弹窗状态 */
const dialogVisible = ref(false);
const saving = ref(false);
const currentId = ref<number | null>(null);
const formRef = ref<InstanceType<typeof BaseForm>>();
const dialogTitle = computed<string>(() => (currentId.value ? '编辑管理员' : '新增管理员'));

/** 表单数据 */
const formModel = reactive<AdminUserSaveParams>({
  username: '',
  realName: '',
  phone: '',
  email: '',
  password: '',
  roleId: 2,
  status: 1,
  remark: '',
});

/** 表单配置 */
const formFields = computed<FormField[]>(() => [
  { prop: 'username', label: '登录账号', span: 12, maxlength: 20, placeholder: '字母开头,3-20 位' },
  { prop: 'realName', label: '姓名', span: 12, maxlength: 20 },
  { prop: 'phone', label: '手机号', span: 12, maxlength: 11 },
  { prop: 'email', label: '邮箱', span: 12 },
  { prop: 'password', label: '密码', type: 'password', span: 12, placeholder: '编辑时留空表示不修改' },
  { prop: 'roleId', label: '角色', type: 'select', span: 12, options: roleOptions.value },
  { prop: 'status', label: '启用状态', type: 'switch', span: 12 },
  { prop: 'remark', label: '备注', type: 'textarea', rows: 3, maxlength: 200 },
]);

/** 表单校验 */
const formRules: FormRules = {
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]{2,19}$/, message: '账号需以字母开头,3-20 位', trigger: 'blur' },
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  roleId: [{ required: true, message: '请选择角色', trigger: 'change' }],
};

/**
 * 打开新增弹窗
 */
function openCreate(): void {
  currentId.value = null;
  Object.assign(formModel, {
    username: '',
    realName: '',
    phone: '',
    email: '',
    password: '',
    roleId: roles.value[1]?.id ?? roles.value[0]?.id ?? 2,
    status: 1,
    remark: '',
  });
  dialogVisible.value = true;
}

/**
 * 打开编辑弹窗
 */
function openEdit(row: AdminUserInfo): void {
  currentId.value = row.id;
  Object.assign(formModel, {
    username: row.username,
    realName: row.realName ?? '',
    phone: row.phone,
    email: row.email ?? '',
    password: '',
    roleId: row.roleId,
    status: row.status,
    remark: row.remark ?? '',
  });
  dialogVisible.value = true;
}

/**
 * 保存
 */
async function handleSave(): Promise<void> {
  if (!formRef.value) {
    return;
  }
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (!currentId.value && !formModel.password) {
    ElMessage.warning('新增管理员必须设置密码');
    return;
  }
  saving.value = true;
  try {
    const payload: AdminUserSaveParams = { ...formModel };
    if (currentId.value) {
      payload.id = currentId.value;
      payload.password = formModel.password || undefined;
      await updateAdminUserApi(currentId.value, payload);
    } else {
      await createAdminUserApi(payload);
    }
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

/**
 * 启用 / 禁用
 */
async function handleStatusChange(row: AdminUserInfo): Promise<void> {
  const nextStatus = row.status === 1 ? 0 : 1;
  await changeAdminStatusApi(row.id, nextStatus);
  ElMessage.success(nextStatus === 1 ? '已启用' : '已禁用');
  await loadData();
}

/**
 * 重置密码
 */
async function handleResetPassword(row: AdminUserInfo): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定将「${row.username}」的密码重置为 123456 吗?`, '提示', { type: 'warning' });
  } catch {
    return;
  }
  await resetAdminPasswordApi(row.id);
  ElMessage.success('密码已重置为 123456');
}

/**
 * 删除
 */
async function handleDelete(row: AdminUserInfo): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除管理员「${row.username}」吗?`, '提示', { type: 'warning' });
  } catch {
    return;
  }
  await deleteAdminUserApi(row.id);
  ElMessage.success('删除成功');
  await loadData();
}

onMounted(async () => {
  roles.value = await getRoleOptionsApi();
});
</script>

<template>
  <div class="page-container">
    <BaseSearch :model="query" :fields="searchFields" :loading="loading" @search="search" @reset="reset" />

    <div class="card">
      <div class="card__header">
        <div>
          <h3>管理员账号</h3>
          <p class="text-secondary">管理端业务操作人员(sys_user),与客户端客户账号相互独立</p>
        </div>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新增管理员
        </el-button>
      </div>

      <BaseTable
        :data="list"
        :columns="columns"
        :loading="loading"
        :total="total"
        :page-num="pagination.pageNum"
        :page-size="pagination.pageSize"
        @update:page-num="handleCurrentChange"
        @update:page-size="handleSizeChange"
      >
        <template #role="{ row }">
          <el-tag :type="row.roleCode === 'SUPER_ADMIN' ? 'danger' : 'info'" size="small" effect="plain">
            {{ row.roleName || '-' }}
          </el-tag>
        </template>
        <template #status="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
        <template #action="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="handleStatusChange(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="primary" @click="handleResetPassword(row)">重置密码</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </BaseTable>
    </div>

    <BaseDialog v-model="dialogVisible" :title="dialogTitle" :loading="saving" width="640px" @confirm="handleSave">
      <BaseForm
        ref="formRef"
        :model="formModel"
        :fields="formFields"
        :rules="formRules"
        label-width="92px"
        :show-actions="false"
      />
    </BaseDialog>
  </div>
</template>

<style scoped lang="scss">
.card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: $spacing-md;

  h3 {
    font-size: $font-size-lg;
  }

  p {
    margin-top: 6px;
    font-size: 12px;
  }
}
</style>
