<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { cusRegisterApi, cusSendSmsCodeApi } from '@/api';
import { useCusUserStore } from '@/mobile/stores/cusUser';

/**
 * 客户端登录 / 注册页(手机端 H5)
 * <p>客户使用手机号注册登录,与后台管理端账号完全隔离</p>
 */
const router = useRouter();
const route = useRoute();
const cusUserStore = useCusUserStore();

/** 当前页签:login-登录 register-注册 */
const activeTab = ref<'login' | 'register'>('login');
const loading = ref(false);
const privacyVisible = ref(false);
const agreed = ref(false);

/** 登录表单 */
const loginForm = reactive({ account: '', password: '' });
/** 注册表单 */
const registerForm = reactive({
  phone: '',
  nickname: '',
  password: '',
  confirmPassword: '',
  smsCode: '',
});

/** 验证码倒计时剩余秒数 */
const smsCountdown = ref(0);
/** 验证码发送中 */
const smsSending = ref(false);
let smsTimer: ReturnType<typeof setInterval> | undefined;

/**
 * 获取短信验证码
 * <p>60 秒倒计时内不可重复获取;演示环境验证码会直接回显在提示中</p>
 */
async function handleSendSmsCode(): Promise<void> {
  if (smsCountdown.value > 0 || smsSending.value) {
    return;
  }
  if (!/^1[3-9]\d{9}$/.test(registerForm.phone)) {
    ElMessage.warning('请先输入正确的手机号');
    return;
  }
  smsSending.value = true;
  try {
    const result = await cusSendSmsCodeApi({ phone: registerForm.phone, scene: 'register' });
    smsCountdown.value = result.resendSeconds || 60;
    smsTimer = setInterval(() => {
      smsCountdown.value -= 1;
      if (smsCountdown.value <= 0) {
        smsCountdown.value = 0;
        if (smsTimer) {
          clearInterval(smsTimer);
          smsTimer = undefined;
        }
      }
    }, 1000);
    // 演示环境后端会回显验证码,直接帮用户填好;接入真实运营商后 debugCode 恒为空
    if (result.debugCode) {
      registerForm.smsCode = result.debugCode;
      ElMessage.success(`验证码已发送(演示环境自动填充):${result.debugCode}`);
    } else {
      ElMessage.success(result.tip || '验证码已发送,请注意查收短信');
    }
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    smsSending.value = false;
  }
}

onBeforeUnmount(() => {
  if (smsTimer) {
    clearInterval(smsTimer);
  }
});

/**
 * 登录
 */
async function handleLogin(): Promise<void> {
  if (!loginForm.account.trim()) {
    ElMessage.warning('请输入手机号或账号');
    return;
  }
  if (!loginForm.password) {
    ElMessage.warning('请输入密码');
    return;
  }
  loading.value = true;
  try {
    await cusUserStore.login({ account: loginForm.account.trim(), password: loginForm.password });
    ElMessage.success('登录成功');
    const redirect = (route.query.redirect as string | undefined) ?? '/chat';
    await router.replace(redirect);
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    loading.value = false;
  }
}

/**
 * 注册
 */
async function handleRegister(): Promise<void> {
  if (!/^1[3-9]\d{9}$/.test(registerForm.phone)) {
    ElMessage.warning('请输入正确的手机号');
    return;
  }
  if (registerForm.password.length < 6) {
    ElMessage.warning('密码至少 6 位');
    return;
  }
  if (registerForm.password !== registerForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致');
    return;
  }
  if (!/^\d{6}$/.test(registerForm.smsCode)) {
    ElMessage.warning('请输入 6 位短信验证码');
    return;
  }
  if (!agreed.value) {
    ElMessage.warning('请先阅读并同意隐私说明');
    return;
  }
  loading.value = true;
  try {
    await cusRegisterApi({
      phone: registerForm.phone,
      password: registerForm.password,
      confirmPassword: registerForm.confirmPassword,
      nickname: registerForm.nickname || undefined,
      smsCode: registerForm.smsCode,
    });
    ElMessage.success('注册成功,请登录');
    loginForm.account = registerForm.phone;
    loginForm.password = registerForm.password;
    activeTab.value = 'login';
  } catch {
    // 错误提示已统一处理
  } finally {
    loading.value = false;
  }
}

/**
 * 切换页签
 */
function switchTab(tab: 'login' | 'register'): void {
  activeTab.value = tab;
}
</script>

<template>
  <div class="m-page m-page--no-tab login-page">
    <!-- 顶部品牌区 -->
    <header class="login-hero">
      <div class="login-hero__logo">
        <el-icon :size="26"><Umbrella /></el-icon>
        <span>安鑫保险</span>
      </div>
      <h1>智能客服助手</h1>
      <p>投保 · 保全 · 退保 · 减保,7×24 小时在线解答</p>
      <div class="login-hero__bubbles">
        <span>犹豫期多久?</span>
        <span>退保能退多少?</span>
        <span>如何变更受益人?</span>
      </div>
    </header>

    <!-- 表单卡片 -->
    <section class="login-card">
      <div class="login-tabs">
        <button
          type="button"
          class="login-tabs__item"
          :class="{ 'is-active': activeTab === 'login' }"
          @click="switchTab('login')"
        >
          登录
        </button>
        <button
          type="button"
          class="login-tabs__item"
          :class="{ 'is-active': activeTab === 'register' }"
          @click="switchTab('register')"
        >
          注册
        </button>
      </div>

      <!-- 登录 -->
      <div v-if="activeTab === 'login'" class="login-form">
        <label class="field">
          <el-icon><User /></el-icon>
          <input v-model="loginForm.account" type="tel" placeholder="手机号 / 账号" autocomplete="username" />
        </label>
        <label class="field">
          <el-icon><Lock /></el-icon>
          <input
            v-model="loginForm.password"
            type="password"
            placeholder="登录密码"
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          />
        </label>
        <button type="button" class="primary-btn" :disabled="loading" @click="handleLogin">
          {{ loading ? '登录中...' : '登录' }}
        </button>
        <p class="form-tip">演示账号:13900000001 / 123456</p>
      </div>

      <!-- 注册 -->
      <div v-else class="login-form">
        <label class="field">
          <el-icon><Iphone /></el-icon>
          <input v-model="registerForm.phone" type="tel" maxlength="11" placeholder="手机号(用于登录)" />
        </label>
        <label class="field field--sms">
          <el-icon><ChatDotRound /></el-icon>
          <input
            v-model="registerForm.smsCode"
            type="text"
            inputmode="numeric"
            maxlength="6"
            placeholder="短信验证码"
          />
          <button
            type="button"
            class="sms-btn"
            :disabled="smsCountdown > 0 || smsSending"
            @click="handleSendSmsCode"
          >
            {{ smsCountdown > 0 ? `${smsCountdown}s 后重发` : smsSending ? '发送中...' : '获取验证码' }}
          </button>
        </label>
        <label class="field">
          <el-icon><Avatar /></el-icon>
          <input v-model="registerForm.nickname" type="text" maxlength="20" placeholder="昵称(选填)" />
        </label>
        <label class="field">
          <el-icon><Lock /></el-icon>
          <input v-model="registerForm.password" type="password" placeholder="设置密码(至少 6 位)" />
        </label>
        <label class="field">
          <el-icon><Lock /></el-icon>
          <input v-model="registerForm.confirmPassword" type="password" placeholder="确认密码" />
        </label>
        <label class="agree-row">
          <input v-model="agreed" type="checkbox" />
          <span>
            我已阅读并同意
            <a href="javascript:void(0)" @click.prevent="privacyVisible = true">《隐私说明》</a>
          </span>
        </label>
        <button type="button" class="primary-btn" :disabled="loading" @click="handleRegister">
          {{ loading ? '注册中...' : '注册并登录' }}
        </button>
        <p class="form-tip">同一个手机号仅可注册一次,手机号将作为登录账号</p>
      </div>
    </section>

    <footer class="login-footer">
      <p>本服务由智能助手提供,涉及具体条款请以保险合同为准</p>
      <a href="/" class="staff-link">我是员工,进入管理端</a>
    </footer>

    <!-- 隐私说明 -->
    <el-dialog v-model="privacyVisible" title="隐私说明" width="86%" align-center>
      <div class="privacy-text">
        <p>1. 我们仅收集为您提供服务所必需的信息:手机号(登录与身份识别)、昵称、咨询内容。</p>
        <p>2. 咨询内容仅用于生成回答与服务质量改进,不会用于营销推送。</p>
        <p>3. 请勿在对话中提供身份证号、银行卡号、保单号等敏感信息;如确需核保,请通过官方客服热线或柜面办理。</p>
        <p>4. 您可随时联系客服删除账号及历史咨询记录。</p>
      </div>
      <template #footer>
        <el-button type="primary" @click="privacyVisible = false">我知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #1b5faa 0%, #3f8ed0 42%, #f4f6fb 42%, #f4f6fb 100%);
}

.login-hero {
  padding: 34px 22px 26px;
  color: #fff;

  &__logo {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 600;
    letter-spacing: 0.5px;
  }

  h1 {
    margin-top: 22px;
    font-size: 26px;
    font-weight: 700;
    letter-spacing: 1px;
  }

  p {
    margin-top: 10px;
    font-size: 13px;
    color: rgba(255, 255, 255, 0.82);
  }

  &__bubbles {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 18px;

    span {
      padding: 6px 12px;
      font-size: 12px;
      border-radius: 14px;
      color: rgba(255, 255, 255, 0.9);
      background: rgba(255, 255, 255, 0.16);
    }
  }
}

.login-card {
  margin: -28px 16px 0;
  padding: 20px 20px 24px;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 12px 32px rgba(16, 40, 70, 0.12);
}

.login-tabs {
  display: flex;
  gap: 24px;
  margin-bottom: 18px;
  border-bottom: 1px solid #eef1f6;

  &__item {
    position: relative;
    padding: 0 2px 10px;
    font-size: 15px;
    color: #98a2b3;
    background: none;
    border: none;
    cursor: pointer;

    &.is-active {
      color: $color-primary;
      font-weight: 600;

      &::after {
        content: '';
        position: absolute;
        left: 0;
        right: 0;
        bottom: -1px;
        height: 2px;
        border-radius: 2px;
        background: $color-primary;
      }
    }
  }
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: #f7f9fc;
  border: 1px solid transparent;
  border-radius: 12px;

  &:focus-within {
    border-color: rgba(27, 95, 170, 0.4);
    background: #fff;
  }

  .el-icon {
    color: #98a2b3;
  }

  input {
    flex: 1;
    width: 100%;
    border: none;
    outline: none;
    background: transparent;
    font-size: 15px;
    color: #1f2d3d;

    &::placeholder {
      color: #b3bccb;
    }
  }

  &--sms {
    padding-right: 8px;
  }
}

.sms-btn {
  flex-shrink: 0;
  padding: 7px 12px;
  font-size: 12px;
  color: $color-primary;
  background: rgba(27, 95, 170, 0.08);
  border: none;
  border-radius: 8px;
  white-space: nowrap;
  cursor: pointer;

  &:disabled {
    color: #98a2b3;
    background: #eef1f6;
    cursor: not-allowed;
  }
}

.agree-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #667085;

  input {
    margin-top: 2px;
    accent-color: $color-primary;
  }

  a {
    color: $color-primary;
    text-decoration: none;
  }
}

.primary-btn {
  height: 46px;
  margin-top: 4px;
  border: none;
  border-radius: 12px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #1b5faa, #3f8ed0);
  box-shadow: 0 8px 18px rgba(27, 95, 170, 0.28);
  cursor: pointer;

  &:disabled {
    opacity: 0.7;
  }
}

.form-tip {
  text-align: center;
  font-size: 11px;
  color: #98a2b3;
}

.login-footer {
  margin-top: auto;
  padding: 26px 20px 30px;
  text-align: center;

  p {
    font-size: 11px;
    color: #98a2b3;
    line-height: 1.7;
  }

  .staff-link {
    display: inline-block;
    margin-top: 14px;
    font-size: 12px;
    color: $color-primary;
    text-decoration: none;
  }
}

.privacy-text {
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 13px;
  line-height: 1.8;
  color: #475467;
}
</style>
