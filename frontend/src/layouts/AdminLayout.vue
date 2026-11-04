<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { FormRules } from 'element-plus';
import { changeAdminPasswordApi } from '@/api';
import { useUserStore } from '@/stores/user';
import { useMenuStore } from '@/stores/menu';
import MenuTree from './MenuTree.vue';

/**
 * 管理端布局:后端菜单驱动的侧边导航 + 顶部栏 + 内容区
 */
const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const menuStore = useMenuStore();

/** 侧边栏折叠状态 */
const collapsed = ref(false);
/** 修改密码弹窗 */
const pwdVisible = ref(false);
const pwdSaving = ref(false);
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });
const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'));
          return;
        }
        callback();
      },
      trigger: 'blur',
    },
  ],
};
const pwdFormRef = ref<{ validate: () => Promise<boolean>; resetFields: () => void }>();

/** 当前高亮菜单 */
const activeMenu = computed<string>(() => route.path);
/** 当前页面标题 */
const pageTitle = computed<string>(() => (route.meta.title as string | undefined) ?? '后台管理');

/**
 * 退出登录:清理本地状态与动态路由
 */
async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定要退出登录吗?', '提示', { type: 'warning' });
  } catch {
    return;
  }
  await userStore.logout();
  menuStore.reset(router);
  await router.replace('/login');
}

/**
 * 打开修改密码弹窗
 */
function openPasswordDialog(): void {
  pwdForm.oldPassword = '';
  pwdForm.newPassword = '';
  pwdForm.confirmPassword = '';
  pwdVisible.value = true;
}

/**
 * 提交修改密码
 */
async function submitPassword(): Promise<void> {
  if (!pwdFormRef.value) {
    return;
  }
  const valid = await pwdFormRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }
  pwdSaving.value = true;
  try {
    await changeAdminPasswordApi({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    });
    ElMessage.success('密码修改成功,请重新登录');
    pwdVisible.value = false;
    await userStore.logout();
    menuStore.reset(router);
    await router.replace('/login');
  } finally {
    pwdSaving.value = false;
  }
}

/**
 * 新窗口打开客户端(手机端)
 */
function openCustomerSite(): void {
  window.open('/mobile.html', '_blank');
}
</script>

<template>
  <el-container class="admin-layout">
    <!-- 侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '228px'" class="admin-aside">
      <div class="admin-logo">
        <el-icon :size="22"><Umbrella /></el-icon>
        <span v-show="!collapsed">安鑫保险 · 管理端</span>
      </div>
      <el-scrollbar class="admin-menu-wrap">
        <el-menu :default-active="activeMenu" :collapse="collapsed" router unique-opened class="admin-menu">
          <MenuTree :items="menuStore.menus" />
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <!-- 顶部栏 -->
      <el-header class="admin-header">
        <div class="admin-header__left">
          <el-icon class="collapse-btn" :size="20" @click="collapsed = !collapsed">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>后台管理</el-breadcrumb-item>
            <el-breadcrumb-item>{{ pageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="admin-header__right">
          <el-button link @click="openCustomerSite">
            <el-icon><Cellphone /></el-icon>客户端 H5
          </el-button>
          <el-dropdown trigger="click">
            <span class="user-entry">
              <el-avatar :size="28" :src="userStore.userInfo?.avatar || undefined">
                <el-icon><UserFilled /></el-icon>
              </el-avatar>
              <span>{{ userStore.displayName }}</span>
              <el-tag size="small" type="info" effect="plain">{{ userStore.roleName }}</el-tag>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="openPasswordDialog">
                  <el-icon><Lock /></el-icon>修改密码
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="admin-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <!-- 修改密码 -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="420px" destroy-on-close>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-32 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="submitPassword">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<style scoped lang="scss">
.admin-layout {
  height: 100vh;
}

.admin-aside {
  display: flex;
  flex-direction: column;
  background: #10243d;
  transition: width 0.2s ease;
  overflow: hidden;

  .admin-logo {
    height: $header-height;
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: 0 18px;
    color: #fff;
    font-size: $font-size-base;
    font-weight: 600;
    white-space: nowrap;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    flex-shrink: 0;
  }

  .admin-menu-wrap {
    flex: 1;
  }
}

.admin-menu {
  border-right: none;
  background: transparent;

  :deep(.el-sub-menu__title),
  :deep(.el-menu-item) {
    color: rgba(255, 255, 255, 0.72);

    &:hover {
      background: rgba(255, 255, 255, 0.06);
      color: #fff;
    }
  }

  :deep(.el-menu-item.is-active) {
    background: $color-primary;
    color: #fff;
  }

  :deep(.el-sub-menu .el-menu) {
    background: rgba(0, 0, 0, 0.18);
  }
}

.admin-header {
  height: $header-height;
  background: #fff;
  border-bottom: 1px solid $color-border-light;
  display: flex;
  align-items: center;
  justify-content: space-between;

  &__left {
    display: flex;
    align-items: center;
    gap: $spacing-md;

    .collapse-btn {
      cursor: pointer;
      color: $color-text-regular;
    }
  }

  &__right {
    display: flex;
    align-items: center;
    gap: $spacing-lg;

    .user-entry {
      display: flex;
      align-items: center;
      gap: $spacing-sm;
      cursor: pointer;
      outline: none;
    }
  }
}

.admin-main {
  background: $color-bg-page;
  padding: 0;
}
</style>
