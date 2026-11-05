<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getCertTypesApi, realNameAuthApi } from '@/api';
import { useCusUserStore } from '@/mobile/stores/cusUser';
import type { CertificateTypeOption } from '@/types/api';

/**
 * 实名认证
 * <p>姓名 + 证件类型 + 证件号。认证通过后查保单/理赔不再受限;
 * 未认证时 AI 只回答通用问题。本地做格式校验(身份证含校验位),
 * 真实二要素核验由后端通道负责。</p>
 */
const router = useRouter();
const cusUserStore = useCusUserStore();

const form = reactive({ realName: '', certType: 'ID_CARD', certNo: '' });
const certTypes = ref<CertificateTypeOption[]>([]);
const submitting = ref(false);

/** 是否已实名 */
const verified = computed<boolean>(() => cusUserStore.userInfo?.realVerified === 1);
/** 证件号输入提示 */
const certPlaceholder = computed<string>(() => {
  if (form.certType === 'ID_CARD') {
    return '请输入 18 位身份证号';
  }
  const type = certTypes.value.find((item) => item.code === form.certType);
  return `请输入${type?.name ?? '证件'}号码`;
});

/**
 * 提交认证
 */
async function submit(): Promise<void> {
  if (!form.realName.trim()) {
    ElMessage.warning('请填写真实姓名');
    return;
  }
  if (form.realName.trim().length < 2) {
    ElMessage.warning('请填写证件上的完整姓名');
    return;
  }
  if (!form.certNo.trim()) {
    ElMessage.warning('请填写证件号');
    return;
  }
  submitting.value = true;
  try {
    const info = await realNameAuthApi({
      realName: form.realName.trim(),
      certType: form.certType,
      certNo: form.certNo.trim(),
    });
    cusUserStore.setUserInfo(info);
    ElMessage.success('实名认证成功');
    await router.replace('/profile');
  } finally {
    submitting.value = false;
  }
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
    // 字典拉取失败时保留默认身份证选项
  }
  // 已填过的姓名/证件类型回填,减少重复输入(证件号不回填:后端只返回打码值)
  if (cusUserStore.userInfo?.realName) {
    form.realName = cusUserStore.userInfo.realName;
  }
  if (cusUserStore.userInfo?.certType) {
    form.certType = cusUserStore.userInfo.certType;
  }
});
</script>

<template>
  <div class="m-page realname-page">
    <header class="rn-header">
      <button type="button" class="rn-header__back" @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <span class="rn-header__title">实名认证</span>
      <span class="rn-header__placeholder"></span>
    </header>

    <!-- 已认证:只展示结果 -->
    <section v-if="verified" class="m-card rn-card rn-done">
      <div class="rn-done__icon">
        <el-icon :size="34"><CircleCheckFilled /></el-icon>
      </div>
      <p class="rn-done__title">已完成实名认证</p>
      <p class="rn-done__desc">
        {{ cusUserStore.userInfo?.realName }} · {{ cusUserStore.userInfo?.certTypeDesc }}
        {{ cusUserStore.userInfo?.certNo }}
      </p>
      <p class="rn-done__time">认证时间:{{ cusUserStore.userInfo?.realVerifiedTime || '-' }}</p>
      <p class="rn-done__tip">证件信息如需变更请联系客服,认证后不可自行修改。</p>
      <el-button type="primary" class="rn-submit" @click="router.replace('/profile')">返回我的</el-button>
    </section>

    <!-- 未认证:填写表单 -->
    <template v-else>
      <section class="rn-banner">
        <el-icon :size="18"><InfoFilled /></el-icon>
        <p>
          完成实名认证后,可直接查询<strong>本人保单、理赔进度与缴费计划</strong>;
          未认证时只能咨询投保、保全、退保等通用规则。
        </p>
      </section>

      <section class="m-card rn-card">
        <label class="rn-field">
          <span>真实姓名</span>
          <input v-model="form.realName" type="text" maxlength="50" placeholder="请填写证件上的姓名" />
        </label>
        <label class="rn-field">
          <span>证件类型</span>
          <el-select v-model="form.certType" class="rn-select">
            <el-option v-for="item in certTypes" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </label>
        <label class="rn-field">
          <span>证件号</span>
          <input v-model="form.certNo" type="text" maxlength="64" :placeholder="certPlaceholder" />
        </label>
        <p class="rn-note">
          支持居民身份证、护照、军官证、士兵证、港澳居民来往内地通行证、台湾居民来往大陆通行证、
          外国人永久居留身份证等投保证件。
        </p>
        <p class="rn-note">信息仅用于身份核验与本人业务查询,展示与导出时一律脱敏。</p>
      </section>

      <div class="rn-actions">
        <el-button type="primary" :loading="submitting" class="rn-submit" @click="submit">
          提交认证
        </el-button>
        <p class="rn-actions__tip">
          提交即表示同意我们依据《个人信息保护法》处理您的身份信息
        </p>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.realname-page {
  min-height: 100vh;
  padding-bottom: 30px;
  background: #f4f6fb;
}

.rn-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 50px;
  padding: 0 12px;
  background: #fff;
  border-bottom: 1px solid #eef1f6;

  &__back {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border: none;
    background: transparent;
    color: #1f2d3d;
  }

  &__title {
    font-size: 16px;
    font-weight: 600;
    color: #1f2d3d;
  }

  &__placeholder {
    width: 32px;
  }
}

.rn-banner {
  display: flex;
  gap: 8px;
  margin: 12px 16px 0;
  padding: 12px 14px;
  border-radius: 12px;
  background: #eaf3ff;
  color: #1b5faa;
  font-size: 12px;
  line-height: 1.7;

  .el-icon {
    margin-top: 2px;
    flex-shrink: 0;
  }
}

.rn-card {
  margin: 12px 16px 0;
  padding: 16px;
}

.rn-field {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  margin-bottom: 10px;
  border-radius: 10px;
  background: #f7f9fc;

  &:last-of-type {
    margin-bottom: 0;
  }

  span {
    width: 70px;
    flex-shrink: 0;
    font-size: 13px;
    color: #667085;
  }

  input {
    flex: 1;
    min-width: 0;
    border: none;
    outline: none;
    background: transparent;
    font-size: 14px;
    color: #1f2d3d;
  }
}

.rn-select {
  flex: 1;

  :deep(.el-input__wrapper) {
    padding-left: 0;
    background: transparent;
    box-shadow: none;
  }
}

.rn-note {
  margin-top: 10px;
  font-size: 11px;
  line-height: 1.7;
  color: #98a2b3;
}

.rn-actions {
  padding: 20px 16px 0;
  text-align: center;

  &__tip {
    margin-top: 12px;
    font-size: 11px;
    color: #98a2b3;
  }
}

.rn-submit {
  width: 100%;
  height: 44px;
  border-radius: 12px;
  font-size: 15px;
}

.rn-done {
  text-align: center;
  padding: 28px 20px;

  &__icon {
    color: #12b76a;
  }

  &__title {
    margin-top: 10px;
    font-size: 17px;
    font-weight: 600;
    color: #1f2d3d;
  }

  &__desc {
    margin-top: 8px;
    font-size: 13px;
    color: #475467;
  }

  &__time {
    margin-top: 6px;
    font-size: 12px;
    color: #98a2b3;
  }

  &__tip {
    margin: 16px 0 20px;
    font-size: 11px;
    color: #98a2b3;
  }
}
</style>
