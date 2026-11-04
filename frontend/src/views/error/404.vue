<script setup lang="ts">
import { useRouter } from 'vue-router';

/**
 * 404 页面
 */
const router = useRouter();

function goHome(): void {
  void router.replace('/');
}

function goBack(): void {
  router.back();
}
</script>

<template>
  <div class="not-found">
    <div class="not-found__code">404</div>
    <h2>页面走丢了</h2>
    <p class="text-secondary">您访问的页面不存在,或者已经被删除。</p>
    <div class="not-found__actions">
      <el-button type="primary" @click="goHome">返回首页</el-button>
      <el-button @click="goBack">返回上一页</el-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.not-found {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  background: $color-bg-page;

  &__code {
    font-size: 96px;
    font-weight: 700;
    line-height: 1;
    background: linear-gradient(120deg, $color-primary, $color-accent);
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
  }

  h2 {
    font-size: $font-size-xl;
  }

  &__actions {
    margin-top: $spacing-md;
    display: flex;
    gap: $spacing-sm;
  }
}
</style>
