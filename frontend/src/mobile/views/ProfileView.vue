<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { changeCusPasswordApi, updateCusProfileApi } from '@/api';
import { useCusUserStore } from '@/mobile/stores/cusUser';

/**
 * 我的:个人资料、密码安全与隐私说明
 */
const router = useRouter();
const cusUserStore = useCusUserStore();

const profileVisible = ref(false);
const passwordVisible = ref(false);
const privacyVisible = ref(false);
const aboutVisible = ref(false);
const saving = ref(false);

/** 资料表单 */
const profileForm = reactive({ nickname: '', email: '' });
/** 密码表单 */
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });

/**
 * 打开资料编辑
 */
function openProfile(): void {
  profileForm.nickname = cusUserStore.userInfo?.nickname ?? '';
  profileForm.email = cusUserStore.userInfo?.email ?? '';
  profileVisible.value = true;
}

/**
 * 保存资料
 */
async function saveProfile(): Promise<void> {
  if (!profileForm.nickname.trim()) {
    ElMessage.warning('昵称不能为空');
    return;
  }
  saving.value = true;
  try {
    const info = await updateCusProfileApi(profileForm.nickname.trim(), profileForm.email.trim());
    cusUserStore.setUserInfo(info);
    ElMessage.success('资料已更新');
    profileVisible.value = false;
  } finally {
    saving.value = false;
  }
}

/**
 * 打开修改密码
 */
function openPassword(): void {
  passwordForm.oldPassword = '';
  passwordForm.newPassword = '';
  passwordForm.confirmPassword = '';
  passwordVisible.value = true;
}

/**
 * 提交修改密码
 */
async function savePassword(): Promise<void> {
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位');
    return;
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致');
    return;
  }
  saving.value = true;
  try {
    await changeCusPasswordApi({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    });
    ElMessage.success('密码已修改,请重新登录');
    passwordVisible.value = false;
    await cusUserStore.logout();
    await router.replace('/login');
  } finally {
    saving.value = false;
  }
}

/**
 * 退出登录
 */
async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定要退出登录吗?', '提示', { type: 'warning' });
  } catch {
    return;
  }
  await cusUserStore.logout();
  await router.replace('/login');
}

onMounted(async () => {
  if (cusUserStore.token && !cusUserStore.userInfo) {
    try {
      await cusUserStore.fetchUserInfo();
    } catch {
      // 登录失效由拦截器统一处理
    }
  }
});
</script>

<template>
  <div class="m-page profile-page">
    <!-- 头部 -->
    <header class="profile-hero">
      <div class="profile-hero__avatar">
        <el-icon :size="30"><UserFilled /></el-icon>
      </div>
      <div class="profile-hero__info">
        <p class="profile-hero__name">{{ cusUserStore.displayName }}</p>
        <p class="profile-hero__phone">{{ cusUserStore.maskedPhone }}</p>
      </div>
      <el-tag size="small" effect="dark" type="primary" round>客户</el-tag>
    </header>

    <!-- 资料卡 -->
    <section class="m-card profile-card">
      <div class="cell" @click="openProfile">
        <el-icon><Edit /></el-icon>
        <span class="cell__label">编辑资料</span>
        <span class="cell__value m-ellipsis">{{ cusUserStore.userInfo?.nickname || '未设置' }}</span>
        <el-icon class="cell__arrow"><ArrowRight /></el-icon>
      </div>
      <div class="cell" @click="openPassword">
        <el-icon><Lock /></el-icon>
        <span class="cell__label">修改密码</span>
        <el-icon class="cell__arrow"><ArrowRight /></el-icon>
      </div>
      <div class="cell" @click="privacyVisible = true">
        <el-icon><CircleCheck /></el-icon>
        <span class="cell__label">隐私与安全</span>
        <el-icon class="cell__arrow"><ArrowRight /></el-icon>
      </div>
      <div class="cell" @click="aboutVisible = true">
        <el-icon><InfoFilled /></el-icon>
        <span class="cell__label">关于我们</span>
        <el-icon class="cell__arrow"><ArrowRight /></el-icon>
      </div>
    </section>

    <!-- 账号信息 -->
    <section class="m-card info-card">
      <p class="info-card__title">账号信息</p>
      <div class="info-row">
        <span>注册时间</span>
        <span>{{ cusUserStore.userInfo?.createTime || '-' }}</span>
      </div>
      <div class="info-row">
        <span>上次登录</span>
        <span>{{ cusUserStore.userInfo?.lastLoginTime || '-' }}</span>
      </div>
      <div class="info-row">
        <span>注册来源</span>
        <span>{{ cusUserStore.userInfo?.registerSource === 'H5' ? '手机端 H5' : cusUserStore.userInfo?.registerSource }}</span>
      </div>
    </section>

    <div class="profile-actions">
      <button type="button" class="logout-btn" @click="handleLogout">退出登录</button>
      <a href="/" class="staff-link">员工入口 · 管理端</a>
    </div>

    <!-- 编辑资料 -->
    <el-dialog v-model="profileVisible" title="编辑资料" width="86%" align-center>
      <div class="dialog-form">
        <label class="dialog-field">
          <span>昵称</span>
          <input v-model="profileForm.nickname" type="text" maxlength="20" placeholder="请输入昵称" />
        </label>
        <label class="dialog-field">
          <span>邮箱</span>
          <input v-model="profileForm.email" type="email" placeholder="选填,用于接收服务通知" />
        </label>
        <p class="dialog-tip">手机号是登录凭证,如需更换请联系客服核身</p>
      </div>
      <template #footer>
        <el-button @click="profileVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码 -->
    <el-dialog v-model="passwordVisible" title="修改密码" width="86%" align-center>
      <div class="dialog-form">
        <label class="dialog-field">
          <span>原密码</span>
          <input v-model="passwordForm.oldPassword" type="password" placeholder="请输入原密码" />
        </label>
        <label class="dialog-field">
          <span>新密码</span>
          <input v-model="passwordForm.newPassword" type="password" placeholder="至少 6 位" />
        </label>
        <label class="dialog-field">
          <span>确认密码</span>
          <input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" />
        </label>
      </div>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePassword">确定</el-button>
      </template>
    </el-dialog>

    <!-- 隐私与安全 -->
    <el-dialog v-model="privacyVisible" title="隐私与安全" width="86%" align-center>
      <div class="dialog-text">
        <p>我们仅收集手机号、昵称与咨询内容,用于身份识别和回答生成。</p>
        <p>对话内容不会用于营销推送,也不会向第三方出售。</p>
        <p>请勿在对话中提交身份证号、银行卡号、保单号等敏感信息。</p>
        <p>如需注销账号或删除历史记录,请联系官方客服处理。</p>
      </div>
      <template #footer>
        <el-button type="primary" @click="privacyVisible = false">我知道了</el-button>
      </template>
    </el-dialog>

    <!-- 关于 -->
    <el-dialog v-model="aboutVisible" title="关于我们" width="86%" align-center>
      <div class="dialog-text">
        <p>安鑫保险智能客服助手,基于大模型与保险知识库,为客户提供 7×24 小时的投保、保全、退保、减保等咨询服务。</p>
        <p>回答仅供参考,具体保障责任、免责条款与办理规则请以保险合同及官方渠道为准。</p>
        <p>客服热线:95500(示例)</p>
      </div>
      <template #footer>
        <el-button type="primary" @click="aboutVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.profile-page {
  background: #f4f6fb;
}

.profile-hero {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 28px 20px 34px;
  color: #fff;
  background: linear-gradient(135deg, #1b5faa, #3f8ed0);

  &__avatar {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 56px;
    height: 56px;
    border-radius: 18px;
    background: rgba(255, 255, 255, 0.2);
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__name {
    font-size: 17px;
    font-weight: 600;
  }

  &__phone {
    margin-top: 4px;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.78);
  }
}

.profile-card {
  margin-top: -20px;
  padding: 4px 16px;
}

.cell {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 15px 0;
  border-bottom: 1px solid #f3f5f9;
  cursor: pointer;

  &:last-child {
    border-bottom: none;
  }

  .el-icon {
    color: #1b5faa;
  }

  &__label {
    flex: 1;
    font-size: 14px;
    color: #1f2d3d;
  }

  &__value {
    max-width: 130px;
    font-size: 12px;
    color: #98a2b3;
  }

  &__arrow {
    color: #c7cfdb !important;
  }
}

.info-card {
  &__title {
    margin-bottom: 10px;
    font-size: 13px;
    font-weight: 600;
    color: #1f2d3d;
  }
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 12px;
  color: #667085;
}

.profile-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 8px 16px 30px;
}

.logout-btn {
  width: 100%;
  height: 44px;
  border: 1px solid rgba(240, 68, 56, 0.24);
  border-radius: 12px;
  background: #fff;
  color: #f04438;
  font-size: 14px;
  cursor: pointer;
}

.staff-link {
  font-size: 12px;
  color: #98a2b3;
  text-decoration: none;
}

.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dialog-field {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f7f9fc;

  span {
    width: 62px;
    font-size: 13px;
    color: #667085;
    flex-shrink: 0;
  }

  input {
    flex: 1;
    border: none;
    outline: none;
    background: transparent;
    font-size: 14px;
    color: #1f2d3d;
  }
}

.dialog-tip {
  font-size: 11px;
  color: #98a2b3;
}

.dialog-text {
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 13px;
  line-height: 1.8;
  color: #475467;
}
</style>
