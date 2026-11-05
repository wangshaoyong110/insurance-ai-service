<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  changeCusPasswordApi,
  getCertTypesApi,
  updateCusProfileApi,
} from '@/api';
import { uploadFileApi } from '@/api/modules/file';
import { useCusUserStore } from '@/mobile/stores/cusUser';
import type { CertificateTypeOption } from '@/types/api';
import { checkUpload } from '@/utils/uploadLimit';

/**
 * 我的:头像、个人资料、实名认证、密码安全与隐私说明
 */
const router = useRouter();
const cusUserStore = useCusUserStore();

const profileVisible = ref(false);
const passwordVisible = ref(false);
const privacyVisible = ref(false);
const aboutVisible = ref(false);
const saving = ref(false);
const avatarUploading = ref(false);
const avatarInput = ref<HTMLInputElement | null>(null);

/** 资料表单 */
const profileForm = reactive({
  nickname: '',
  email: '',
  realName: '',
  gender: 0,
  birthday: '',
  certType: '',
  certNo: '',
});
/** 密码表单 */
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });
/** 证件类型字典 */
const certTypes = ref<CertificateTypeOption[]>([]);

/** 是否已实名 */
const verified = computed<boolean>(() => cusUserStore.userInfo?.realVerified === 1);
/** 头像地址(后端返回的是 /api/upload/... 相对路径) */
const avatarUrl = computed<string>(() => cusUserStore.userInfo?.avatar || '');
/** 已实名时证件信息只读,避免自行改证件绕过核验 */
const certReadonly = computed<boolean>(() => verified.value);

/**
 * 打开资料编辑
 */
function openProfile(): void {
  const info = cusUserStore.userInfo;
  profileForm.nickname = info?.nickname ?? '';
  profileForm.email = info?.email ?? '';
  profileForm.realName = info?.realName ?? '';
  profileForm.gender = info?.gender ?? 0;
  profileForm.birthday = info?.birthday ?? '';
  profileForm.certType = info?.certType ?? '';
  // 后端返回的是打码证件号,已实名时不回填(避免把 3****2 当作完整证件号再提交)
  profileForm.certNo = certReadonly.value ? '' : (info?.certNo ?? '');
  profileVisible.value = true;
}

/**
 * 选择头像文件
 */
function pickAvatar(): void {
  avatarInput.value?.click();
}

/**
 * 上传头像:先本地预检大小,再走后端上传(avatar 业务限 2MB),最后把 url 存到资料
 */
async function onAvatarChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) {
    return;
  }
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('头像只支持图片格式');
    return;
  }
  const error = checkUpload(file, 'avatar');
  if (error) {
    ElMessage.warning(error);
    return;
  }
  avatarUploading.value = true;
  try {
    const uploaded = await uploadFileApi(file, 'avatar');
    const info = await updateCusProfileApi({ avatar: uploaded.url });
    cusUserStore.setUserInfo(info);
    ElMessage.success('头像已更新');
  } finally {
    avatarUploading.value = false;
  }
}

/**
 * 保存资料
 */
async function saveProfile(): Promise<void> {
  if (!profileForm.nickname.trim()) {
    ElMessage.warning('昵称不能为空');
    return;
  }
  const payload: Parameters<typeof updateCusProfileApi>[0] = {
    nickname: profileForm.nickname.trim(),
    email: profileForm.email.trim(),
    realName: profileForm.realName.trim(),
    gender: profileForm.gender,
    birthday: profileForm.birthday || undefined,
  };
  if (!certReadonly.value && (profileForm.certType || profileForm.certNo)) {
    if (!profileForm.certType) {
      ElMessage.warning('请选择证件类型');
      return;
    }
    if (!profileForm.certNo.trim()) {
      ElMessage.warning('请填写证件号');
      return;
    }
    payload.certType = profileForm.certType;
    payload.certNo = profileForm.certNo.trim();
  }
  saving.value = true;
  try {
    const info = await updateCusProfileApi(payload);
    cusUserStore.setUserInfo(info);
    ElMessage.success('资料已更新');
    profileVisible.value = false;
  } finally {
    saving.value = false;
  }
}

/**
 * 实名认证入口:已实名只提示,未实名跳转认证页
 */
function goRealName(): void {
  if (verified.value) {
    ElMessage.success(`已实名认证(${cusUserStore.userInfo?.certTypeDesc ?? ''} ${cusUserStore.userInfo?.certNo ?? ''})`);
    return;
  }
  void router.push('/realname');
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
  try {
    certTypes.value = await getCertTypesApi();
  } catch {
    // 字典拉取失败不影响页面,下拉为空时用户可稍后重试
  }
});
</script>

<template>
  <div class="m-page profile-page">
    <!-- 头部:点头像即可上传 -->
    <header class="profile-hero">
      <button type="button" class="profile-hero__avatar" :disabled="avatarUploading" @click="pickAvatar">
        <img v-if="avatarUrl" :src="avatarUrl" alt="头像" />
        <el-icon v-else :size="30"><UserFilled /></el-icon>
        <span class="profile-hero__camera">
          <el-icon :size="12"><Camera /></el-icon>
        </span>
      </button>
      <input ref="avatarInput" type="file" accept="image/*" class="avatar-input" @change="onAvatarChange" />
      <div class="profile-hero__info">
        <p class="profile-hero__name">{{ cusUserStore.displayName }}</p>
        <p class="profile-hero__phone">{{ cusUserStore.maskedPhone }}</p>
      </div>
      <div class="profile-hero__tags">
        <el-tag size="small" effect="dark" type="primary" round>客户</el-tag>
        <el-tag
          size="small"
          effect="dark"
          round
          :type="verified ? 'success' : 'warning'"
          class="verify-tag"
          @click="goRealName"
        >
          {{ verified ? '已实名' : '未实名' }}
        </el-tag>
      </div>
    </header>

    <!-- 实名认证状态卡 -->
    <section class="m-card verify-card" @click="goRealName">
      <div class="verify-card__icon" :class="{ 'verify-card__icon--done': verified }">
        <el-icon :size="20"><CircleCheck v-if="verified" /><Warning v-else /></el-icon>
      </div>
      <div class="verify-card__body">
        <p class="verify-card__title">{{ verified ? '已完成实名认证' : '未实名认证' }}</p>
        <p class="verify-card__desc">
          {{
            verified
              ? `${cusUserStore.userInfo?.realName ?? ''} · ${cusUserStore.userInfo?.certTypeDesc ?? ''} ${cusUserStore.userInfo?.certNo ?? ''}`
              : '完成认证后可查询本人保单、理赔进度与缴费计划'
          }}
        </p>
      </div>
      <span class="verify-card__action">{{ verified ? '查看' : '去认证' }}</span>
      <el-icon class="cell__arrow"><ArrowRight /></el-icon>
    </section>

    <!-- 资料卡 -->
    <section class="m-card profile-card">
      <div class="cell" @click="openProfile">
        <el-icon><Edit /></el-icon>
        <span class="cell__label">编辑资料</span>
        <span class="cell__value m-ellipsis">{{ cusUserStore.userInfo?.nickname || '未设置' }}</span>
        <el-icon class="cell__arrow"><ArrowRight /></el-icon>
      </div>
      <div class="cell" @click="router.push('/tickets')">
        <el-icon><Tickets /></el-icon>
        <span class="cell__label">我的工单</span>
        <span class="cell__value m-ellipsis">转人工服务进度</span>
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
        <span>实名认证</span>
        <span>{{ verified ? `已认证 ${cusUserStore.userInfo?.realVerifiedTime ?? ''}` : '未认证' }}</span>
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
          <span>姓名</span>
          <input v-model="profileForm.realName" type="text" maxlength="50" placeholder="选填,可与证件姓名一致" />
        </label>
        <div class="dialog-field">
          <span>性别</span>
          <el-radio-group v-model="profileForm.gender" class="dialog-radio">
            <el-radio :value="0">不填</el-radio>
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </div>
        <div class="dialog-field">
          <span>生日</span>
          <el-date-picker
            v-model="profileForm.birthday"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择出生日期"
            :disabled-date="(date: Date) => date.getTime() > Date.now()"
            class="dialog-date"
          />
        </div>
        <div class="dialog-field">
          <span>证件</span>
          <el-select
            v-model="profileForm.certType"
            placeholder="证件类型"
            :disabled="certReadonly"
            class="dialog-select"
          >
            <el-option v-for="item in certTypes" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </div>
        <label class="dialog-field">
          <span>证件号</span>
          <input
            v-model="profileForm.certNo"
            type="text"
            maxlength="64"
            :disabled="certReadonly"
            :placeholder="certReadonly ? '已实名,证件号不可修改' : '请输入证件号'"
          />
        </label>
        <label class="dialog-field">
          <span>邮箱</span>
          <input v-model="profileForm.email" type="email" placeholder="选填,用于接收服务通知" />
        </label>
        <p class="dialog-tip">
          {{ certReadonly ? '已完成实名认证,证件信息不可自行修改,如需变更请联系客服' : '证件号仅用于实名认证与业务查询,展示时会自动打码' }}
        </p>
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
        <p>我们仅收集手机号、昵称、实名认证信息与咨询内容,用于身份识别和回答生成。</p>
        <p>证件号仅用于实名认证与本人业务查询,页面与导出文件中一律脱敏展示。</p>
        <p>对话内容不会用于营销推送,也不会向第三方出售。</p>
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
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 56px;
    height: 56px;
    padding: 0;
    overflow: hidden;
    border: none;
    border-radius: 18px;
    background: rgba(255, 255, 255, 0.2);
    cursor: pointer;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  &__camera {
    position: absolute;
    right: 0;
    bottom: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border-radius: 9px;
    background: rgba(0, 0, 0, 0.45);
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

  &__tags {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 6px;
  }
}

.verify-tag {
  cursor: pointer;
}

.avatar-input {
  display: none;
}

.verify-card {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: -20px;
  padding: 14px 16px;

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: 12px;
    color: #b54708;
    background: #fef0c7;

    &--done {
      color: #067647;
      background: #dcfae6;
    }
  }

  &__body {
    flex: 1;
    min-width: 0;
  }

  &__title {
    font-size: 14px;
    font-weight: 600;
    color: #1f2d3d;
  }

  &__desc {
    margin-top: 3px;
    font-size: 11px;
    color: #98a2b3;
  }

  &__action {
    font-size: 12px;
    color: #1b5faa;
  }
}

.profile-card {
  margin-top: 12px;
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

    &:disabled {
      color: #98a2b3;
    }
  }
}

.dialog-radio {
  flex: 1;
}

.dialog-date,
.dialog-select {
  flex: 1;

  :deep(.el-input__wrapper) {
    padding-left: 0;
    background: transparent;
    box-shadow: none;
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
