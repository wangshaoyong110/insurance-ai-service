<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import type { FormRules } from 'element-plus';
import type { ElTree } from 'element-plus';
import type BaseForm from '@/components/BaseForm/index.vue';
import {
  createRoleApi,
  deleteRoleApi,
  getMenuTreeApi,
  getRoleDetailApi,
  getRoleListApi,
  updateRoleApi,
} from '@/api';
import type { FormField, MenuItem, RoleItem } from '@/types';
import type { RoleSaveParams } from '@/types/api';

/**
 * 角色管理:维护角色基础信息与菜单授权
 * <p>授权结果决定该角色登录后台后可见的导航菜单</p>
 */
const loading = ref(false);
const roles = ref<RoleItem[]>([]);
const menuTree = ref<MenuItem[]>([]);

/** 弹窗状态 */
const dialogVisible = ref(false);
const saving = ref(false);
const currentId = ref<number | null>(null);
const formRef = ref<InstanceType<typeof BaseForm>>();
const treeRef = ref<InstanceType<typeof ElTree>>();
const dialogTitle = computed<string>(() => (currentId.value ? '编辑角色' : '新增角色'));

/** 表单数据 */
const formModel = reactive<RoleSaveParams>({
  roleCode: '',
  roleName: '',
  sort: 0,
  status: 1,
  remark: '',
});

/** 表单配置 */
const formFields: FormField[] = [
  { prop: 'roleCode', label: '角色标识', span: 12, placeholder: '大写字母,如 AUDITOR' },
  { prop: 'roleName', label: '角色名称', span: 12, maxlength: 20 },
  { prop: 'sort', label: '排序', type: 'number', span: 12 },
  { prop: 'status', label: '是否启用', type: 'switch', span: 12 },
  { prop: 'remark', label: '备注', type: 'textarea', rows: 2, maxlength: 200 },
];

/** 校验规则 */
const formRules: FormRules = {
  roleCode: [
    { required: true, message: '请输入角色标识', trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]{2,29}$/, message: '需为大写字母、数字或下划线', trigger: 'blur' },
  ],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
};

/**
 * 加载角色列表与菜单树
 */
async function loadData(): Promise<void> {
  loading.value = true;
  try {
    const [roleList, tree] = await Promise.all([getRoleListApi(), getMenuTreeApi(true)]);
    roles.value = roleList;
    menuTree.value = tree;
  } finally {
    loading.value = false;
  }
}

/** 仅保留菜单/目录节点,便于勾选授权 */
const treeProps = { label: 'menuName', children: 'children' } as const;

/**
 * 打开新增弹窗
 */
function openCreate(): void {
  currentId.value = null;
  Object.assign(formModel, { roleCode: '', roleName: '', sort: 0, status: 1, remark: '' });
  dialogVisible.value = true;
  void nextTick(() => treeRef.value?.setCheckedKeys([]));
}

/**
 * 打开编辑弹窗(回显已授权菜单)
 */
async function openEdit(row: unknown): Promise<void> {
  // el-table 插槽的 row 为通用行类型,这里统一收窄
  const role = row as RoleItem;
  if (role.roleCode === 'SUPER_ADMIN') {
    ElMessage.warning('内置超级管理员角色拥有全部菜单,不可编辑');
    return;
  }
  currentId.value = role.id;
  Object.assign(formModel, {
    roleCode: role.roleCode,
    roleName: role.roleName,
    sort: role.sort,
    status: role.status,
    remark: role.remark ?? '',
  });
  dialogVisible.value = true;
  const detail = await getRoleDetailApi(role.id);
  await nextTick();
  treeRef.value?.setCheckedKeys(detail.menuIds ?? []);
}

/**
 * 保存角色
 */
async function handleSave(): Promise<void> {
  if (!formRef.value) {
    return;
  }
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }
  saving.value = true;
  try {
    const checked = (treeRef.value?.getCheckedKeys(false) ?? []) as number[];
    const halfChecked = (treeRef.value?.getHalfCheckedKeys() ?? []) as number[];
    const payload: RoleSaveParams = { ...formModel, menuIds: [...checked, ...halfChecked] };
    if (currentId.value) {
      await updateRoleApi(currentId.value, payload);
    } else {
      await createRoleApi(payload);
    }
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

/**
 * 删除角色
 */
async function handleDelete(row: unknown): Promise<void> {
  const role = row as RoleItem;
  try {
    await ElMessageBox.confirm(`确定删除角色「${role.roleName}」吗?`, '提示', { type: 'warning' });
  } catch {
    return;
  }
  await deleteRoleApi(role.id);
  ElMessage.success('删除成功');
  await loadData();
}

onMounted(loadData);
</script>

<template>
  <div class="page-container">
    <div class="card">
      <div class="card__header">
        <div>
          <h3>角色管理</h3>
          <p class="text-secondary">为不同岗位分配菜单权限,客服专员默认只能看到业务菜单</p>
        </div>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新增角色
        </el-button>
      </div>

      <el-table v-loading="loading" :data="roles" border>
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="roleCode" label="角色标识" min-width="150">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.roleCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="userCount" label="账号数" width="90" align="center" />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑授权</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <BaseDialog v-model="dialogVisible" :title="dialogTitle" :loading="saving" width="720px" @confirm="handleSave">
      <BaseForm
        ref="formRef"
        :model="formModel"
        :fields="formFields"
        :rules="formRules"
        label-width="92px"
        :show-actions="false"
      />
      <div class="menu-auth">
        <p class="menu-auth__title">菜单授权</p>
        <el-tree
          ref="treeRef"
          :data="menuTree"
          :props="treeProps"
          node-key="id"
          show-checkbox
          default-expand-all
          class="menu-auth__tree"
        />
      </div>
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

.menu-auth {
  margin-top: $spacing-md;

  &__title {
    font-size: 13px;
    font-weight: 600;
    color: $color-text-primary;
    margin-bottom: 8px;
  }

  &__tree {
    max-height: 300px;
    overflow: auto;
    padding: 8px 12px;
    border: 1px solid $color-border-light;
    border-radius: $radius-md;
  }
}
</style>
