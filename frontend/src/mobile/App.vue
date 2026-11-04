<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

/**
 * 手机端根组件:内容区 + 底部导航(登录页不显示导航)
 */
const route = useRoute();

/** 是否显示底部导航 */
const showTabBar = computed<boolean>(() => Boolean(route.meta.tab));

/** 底部导航项 */
const tabs = [
  { path: '/chat', label: '咨询', icon: 'ChatDotRound' },
  { path: '/history', label: '历史', icon: 'Clock' },
  { path: '/profile', label: '我的', icon: 'User' },
];
</script>

<template>
  <div class="m-app">
    <router-view v-slot="{ Component }">
      <transition name="m-fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>

    <!-- 底部导航 -->
    <nav v-if="showTabBar" class="m-tabbar">
      <router-link
        v-for="item in tabs"
        :key="item.path"
        :to="item.path"
        class="m-tabbar__item"
        :class="{ 'is-active': route.path === item.path }"
      >
        <el-icon :size="20"><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </router-link>
    </nav>
  </div>
</template>
