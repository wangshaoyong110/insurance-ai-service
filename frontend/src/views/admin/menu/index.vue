<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import type { FormRules } from 'element-plus';
import type BaseForm from '@/components/BaseForm/index.vue';
import { createMenuApi, deleteMenuApi, getMenuTreeApi, updateMenuApi } from '@/api';
import type { FormField, MenuItem } from '@/types';
import type { MenuSaveParams } from '@/types/api';

/**
 * 菜单管理:维护后台导航与页面路由
 * <p>菜单数据直接驱动前端路由注册与侧边栏渲染</p>
 */
const loading = ref(false);
const tree = ref<MenuItem[]>([]);

/** 菜单类型选项 */
const menuTypeOptions = [
  { label: '目录', value: 'DIR' },
  { label: '菜单', value: 'MENU' },
  { label: '按钮', value: 'BUTTON' },
];

/** 弹窗状态 */
const dialogVisible = ref(false);
const saving = ref(false);
const currentId = ref<number | null>(null);
const formRef = ref<InstanceType<typeof BaseForm>>();
const dialogTitle = computed<string>(() => (currentId.value ? '编辑菜单' : '新增菜单'));

/** 表单数据 */
const formModel = reactive<MenuSaveParams>({
  parentId: 0,
  menuName: '',
  menuType: 'MENU',
  path: '',
  component: '',
  icon: '',
  perms: '',
  sort: 0,
  visible: 1,
  status: 1,
  remark: '',
});

/**
 * 上级菜单选项:顶级 + 目录/菜单节点(按钮不能作为父级)
 */
const parentOptions = computed(() => {
  const options: Array<{ label: string; value: number }> = [{ label: '顶级菜单', value: 0 }];
  const walk = (items: MenuItem[], depth: number): void => {
    items.forEach((item) => {
      if (item.menuType !== 'BUTTON') {
        options.push({ label: `${'　'.repeat(depth)}${item.menuName}`, value: item.id });
      }
      if (item.children?.length) {
        walk(item.children, depth + 1);
      }
    });
  };
  walk(tree.value, 0);
  return options;
});

/** 表单配置 */
const formFields = computed<FormField[]>(() => [
  { prop: 'parentId', label: '上级菜单', type: 'select', span: 12, options: parentOptions.value },
  { prop: 'menuName', label: '菜单名称', span: 12, maxlength: 20 },
  { prop: 'menuType', label: '菜单类型', type: 'select', span: 12, options: menuTypeOptions },
  { prop: 'sort', label: '排序', type: 'number', span: 12 },
  { prop: 'path', label: '路由地址', span: 12, placeholder: '如 /system/menu' },
  { prop: 'component', label: '组件路径', span: 12, placeholder: '如 admin/menu/index' },
  { prop: 'icon', label: '图标', span: 12, placeholder: 'Element Plus 图标名,如 Menu' },
  { prop: 'perms', label: '权限标识', span: 12, placeholder: '如 sys:menu:list' },
  { prop: 'visible', label: '是否显示', type: 'switch', span: 12 },
  { prop: 'status', label: '是否启用', type: 'switch', span: 12 },
  { prop: 'remark', label: '备注', type: 'textarea', rows: 2, maxlength: 200 },
]);

/** 校验规则 */
const formRules: FormRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
};

/**
 * 加载菜单树
 */
async function loadTree(): Promise<void> {
  loading.value = true;
  try {
    tree.value = await getMenuTreeApi(false);
  } finally {
    loading.value = false;
  }
}

/**
 * 打开新增弹窗
 */
function openCreate(parent?: unknown): void {
  // el-table 插槽的 row 为通用行类型,这里统一收窄为菜单类型
  const parentMenu = parent as MenuItem | undefined;
  currentId.value = null;
  Object.assign(formModel, {
    parentId: parentMenu?.id ?? 0,
    menuName: '',
    menuType: parentMenu ? 'MENU' : 'DIR',
    path: '',
    component: '',
    icon: '',
    perms: '',
    sort: 0,
    visible: 1,
    status: 1,
    remark: '',
  });
  dialogVisible.value = true;
}

/**
 * 打开编辑弹窗
 */
function openEdit(row: unknown): void {
  const menu = row as MenuItem;
  currentId.value = menu.id;
  Object.assign(formModel, {
    parentId: menu.parentId,
    menuName: menu.menuName,
    menuType: menu.menuType,
    path: menu.path ?? '',
    component: menu.component ?? '',
    icon: menu.icon ?? '',
    perms: menu.perms ?? '',
    sort: menu.sort,
    visible: menu.visible,
    status: menu.status,
    remark: menu.remark ?? '',
  });
  dialogVisible.value = true;
}

/**
 * 保存菜单
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
    const payload: MenuSaveParams = { ...formModel };
    if (currentId.value) {
      await updateMenuApi(currentId.value, payload);
    } else {
      await createMenuApi(payload);
    }
    ElMessage.success('保存成功,重新登录后生效');
    dialogVisible.value = false;
    await loadTree();
  } finally {
    saving.value = false;
  }
}

/**
 * 删除菜单
 */
async function handleDelete(row: unknown): Promise<void> {
  const menu = row as MenuItem;
  try {
    await ElMessageBox.confirm(`确定删除菜单「${menu.menuName}」吗?`, '提示', { type: 'warning' });
  } catch {
    return;
  }
  await deleteMenuApi(menu.id);
  ElMessage.success('删除成功');
  await loadTree();
}

onMounted(loadTree);
</script>

<template>
  <div class="page-container">
    <div class="card">
      <div class="card__header">
        <div>
          <h3>菜单管理</h3>
          <p class="text-secondary">菜单驱动后台路由与侧边导航,新增后需重新登录生效</p>
        </div>
        <el-button type="primary" @click="openCreate()">
          <el-icon><Plus /></el-icon>新增菜单
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tree"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="200">
          <template #default="{ row }">
            <span class="menu-name">
              <el-icon v-if="row.icon"><component :is="row.icon" /></el-icon>
              {{ row.menuName }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="menuType" label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.menuType === 'DIR' ? 'warning' : row.menuType === 'MENU' ? 'primary' : 'info'"
            >
              {{ row.menuType === 'DIR' ? '目录' : row.menuType === 'MENU' ? '菜单' : '按钮' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由地址" min-width="150" show-overflow-tooltip />
        <el-table-column prop="component" label="组件路径" min-width="170" show-overflow-tooltip />
        <el-table-column prop="perms" label="权限标识" min-width="140" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column prop="visible" label="显示" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.visible === 1 ? 'success' : 'info'">
              {{ row.visible === 1 ? '显示' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.menuType !== 'BUTTON'" link type="primary" @click="openCreate(row)">
              新增子项
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <BaseDialog v-model="dialogVisible" :title="dialogTitle" :loading="saving" width="680px" @confirm="handleSave">
      <BaseForm
        ref="formRef"
        :model="formModel"
        :fields="formFields"
        :rules="formRules"
        label-width="96px"
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

.menu-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
</style>
