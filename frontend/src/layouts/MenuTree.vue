<script setup lang="ts">
import type { MenuItem } from '@/types';

/**
 * 递归菜单树:根据后端返回的菜单数据渲染侧边导航
 * <p>目录渲染为可展开的子菜单,菜单项渲染为可点击的导航项</p>
 */
defineProps<{
  /** 菜单节点列表 */
  items: MenuItem[];
}>();
</script>

<template>
  <template v-for="item in items" :key="item.id">
    <!-- 有子节点:渲染为可展开的子菜单 -->
    <el-sub-menu v-if="item.children && item.children.length" :index="item.path || `dir-${item.id}`">
      <template #title>
        <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
        <span>{{ item.menuName }}</span>
      </template>
      <MenuTree :items="item.children" />
    </el-sub-menu>
    <!-- 叶子菜单 -->
    <el-menu-item v-else-if="item.menuType === 'MENU'" :index="item.path ?? ''">
      <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
      <template #title>{{ item.menuName }}</template>
    </el-menu-item>
  </template>
</template>
