<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { FormRules } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { useMenuStore } from '@/stores/menu';

/**
 * 管理端登录页(业务操作人员)
 * <p>与客户端客户登录完全独立:账号体系不同、接口不同、token 不同</p>
 */
const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const menuStore = useMenuStore();

const formRef = ref<{ validate: () => Promise<boolean> }>();
const loading = ref(false);
const form = reactive({
  account: '',
  password: '',
});

const rules: FormRules = {
  account: [{ required: true, message: '请输入账号或手机号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 位', trigger: 'blur' },
  ],
};

/**
 * 提交登录
 */
async function handleLogin(): Promise<void> {
  if (!formRef.value) {
    return;
  }
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }
  loading.value = true;
  try {
    // 切换账号前清理上一位管理员注册的动态路由与菜单
    menuStore.reset(router);
    await userStore.login({ account: form.account.trim(), password: form.password });
    const redirect = (route.query.redirect as string | undefined) ?? '/';
    await router.replace(redirect);
    ElMessage.success('登录成功');
  } catch {
    // 错误提示已在响应拦截器统一处理
  } finally {
    loading.value = false;
  }
}

/**
 * 新窗口打开客户端
 */
function openCustomerSite(): void {
  window.open('/mobile.html', '_blank');
}
</script>

<template>
  <div class="admin-login">
    <div class="admin-login__inner">
      <!-- 左侧品牌区 -->
      <aside class="admin-login__brand">
        <div class="brand-logo">
          <el-icon :size="30"><Umbrella /></el-icon>
          <span>安鑫保险</span>
        </div>
        <h1>智能客服管理平台</h1>
        <p class="brand-desc">
          面向业务操作人员的后台工作台,支持知识库维护、客户服务管理、菜单与角色权限配置。
        </p>
        <ul class="brand-list">
          <li><el-icon><Reading /></el-icon>知识库驱动的智能问答</li>
          <li><el-icon><User /></el-icon>客户与坐席双体系管理</li>
          <li><el-icon><Menu /></el-icon>菜单与角色灵活授权</li>
          <li><el-icon><Document /></el-icon>登录与操作日志全留痕</li>
        </ul>
        <div class="brand-footer">© 安鑫保险 · 仅限内部员工使用</div>
      </aside>

      <!-- 右侧登录表单 -->
      <section class="admin-login__form">
        <header class="form-header">
          <h2>管理端登录</h2>
          <p>请使用后台分配的员工账号登录</p>
        </header>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
          <el-form-item prop="account">
            <el-input v-model="form.account" placeholder="账号 / 手机号" clearable>
              <template #prefix>
                <el-icon><User /></el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="登录密码" show-password>
              <template #prefix>
                <el-icon><Lock /></el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            登录后台
          </el-button>
        </el-form>

        <div class="form-tips">
          <span>演示账号:admin / 123456(超管)、operator / 123456(客服专员)</span>
        </div>

        <div class="form-divider"><span>我是客户</span></div>
        <el-button class="customer-btn" @click="openCustomerSite">
          <el-icon><Cellphone /></el-icon>进入手机端咨询
        </el-button>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.admin-login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl;
  background: linear-gradient(135deg, #0d2a4d 0%, #1b5faa 52%, #2f80c9 100%);

  &__inner {
    width: 100%;
    max-width: 920px;
    min-height: 520px;
    display: grid;
    grid-template-columns: 1.05fr 1fr;
    border-radius: 20px;
    overflow: hidden;
    box-shadow: 0 24px 60px rgba(6, 30, 60, 0.35);
    background: #fff;
  }

  &__brand {
    position: relative;
    padding: 44px 40px;
    color: #fff;
    background: linear-gradient(160deg, #123c6b, #1b5faa 70%);
    display: flex;
    flex-direction: column;

    &::after {
      content: '';
      position: absolute;
      right: -60px;
      bottom: -70px;
      width: 220px;
      height: 220px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.07);
    }

    .brand-logo {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 18px;
      font-weight: 600;
      letter-spacing: 1px;
    }

    h1 {
      margin-top: 42px;
      font-size: 28px;
      font-weight: 700;
      line-height: 1.4;
    }

    .brand-desc {
      margin-top: 14px;
      font-size: 13px;
      line-height: 1.9;
      color: rgba(255, 255, 255, 0.76);
    }

    .brand-list {
      margin-top: 26px;
      display: flex;
      flex-direction: column;
      gap: 12px;

      li {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 13px;
        color: rgba(255, 255, 255, 0.9);
      }
    }

    .brand-footer {
      margin-top: auto;
      font-size: 12px;
      color: rgba(255, 255, 255, 0.5);
    }
  }

  &__form {
    padding: 56px 44px 40px;
    display: flex;
    flex-direction: column;
    justify-content: center;

    .form-header {
      margin-bottom: 28px;

      h2 {
        font-size: 22px;
        font-weight: 700;
        color: $color-text-primary;
      }

      p {
        margin-top: 8px;
        font-size: 13px;
        color: $color-text-secondary;
      }
    }

    .login-btn {
      width: 100%;
      height: 44px;
      margin-top: 6px;
      font-size: 15px;
    }

    .form-tips {
      margin-top: 14px;
      font-size: 12px;
      color: $color-text-secondary;
      line-height: 1.7;
    }

    .form-divider {
      position: relative;
      margin: 26px 0 16px;
      text-align: center;

      &::before {
        content: '';
        position: absolute;
        top: 50%;
        left: 0;
        right: 0;
        height: 1px;
        background: $color-border-light;
      }

      span {
        position: relative;
        padding: 0 12px;
        background: #fff;
        font-size: 12px;
        color: $color-text-secondary;
      }
    }

    .customer-btn {
      width: 100%;
      height: 42px;
    }
  }
}

@media (max-width: 860px) {
  .admin-login__inner {
    grid-template-columns: 1fr;
    max-width: 420px;
  }

  .admin-login__brand {
    display: none;
  }
}
</style>
