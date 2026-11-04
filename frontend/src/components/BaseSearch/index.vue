<script setup lang="ts">
import { computed } from 'vue';
import type { FormField } from '@/types';

/**
 * BaseSearch:基于 el-form 的查询条件封装
 * - schema 驱动,自动渲染查询项
 * - 内置查询 / 重置按钮,支持右侧扩展操作
 *
 * 说明:model 使用 object 类型以兼容业务 interface,内部按键读写。
 */
interface Props {
  /** 查询条件对象(父组件持有) */
  model: object;
  /** 查询项配置 */
  fields: FormField[];
  /** 查询按钮 loading */
  loading?: boolean;
  /** 标签宽度 */
  labelWidth?: string;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  labelWidth: '80px',
});

const emit = defineEmits<{
  (e: 'search'): void;
  (e: 'reset'): void;
}>();

const modelRecord = computed<Record<string, unknown>>(() => props.model as Record<string, unknown>);

function getValue(prop: string): unknown {
  return modelRecord.value[prop];
}

/** 写入查询条件 */
function setField(prop: string, value: unknown): void {
  modelRecord.value[prop] = value;
}

function asString(value: unknown): string {
  return value === null || value === undefined ? '' : String(value);
}

/** unknown -> el-select 可接受的基础值 */
function asFieldValue(value: unknown): string | number | boolean | undefined {
  if (value === null || value === undefined) {
    return undefined;
  }
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return value;
  }
  return String(value);
}

/** 回车快速查询 */
function handleEnter(): void {
  emit('search');
}
</script>

<template>
  <el-form :model="props.model" :label-width="props.labelWidth" inline class="base-search">
    <el-form-item v-for="field in props.fields" :key="field.prop" :label="field.label">
      <el-select
        v-if="field.type === 'select'"
        :model-value="asFieldValue(getValue(field.prop))"
        :placeholder="field.placeholder ?? `请选择${field.label}`"
        :clearable="field.clearable ?? true"
        :style="{ width: field.width ?? '160px' }"
        @update:model-value="(value: unknown) => setField(field.prop, value)"
      >
        <el-option
          v-for="option in field.options ?? []"
          :key="String(option.value)"
          :label="option.label"
          :value="option.value"
        />
      </el-select>

      <el-date-picker
        v-else-if="field.type === 'date'"
        :model-value="getValue(field.prop) as string"
        type="date"
        value-format="YYYY-MM-DD"
        :placeholder="field.placeholder ?? '请选择日期'"
        :style="{ width: field.width ?? '160px' }"
        @update:model-value="(value: unknown) => setField(field.prop, value)"
      />

      <el-input
        v-else
        :model-value="asString(getValue(field.prop))"
        :placeholder="field.placeholder ?? `请输入${field.label}`"
        :clearable="field.clearable ?? true"
        :style="{ width: field.width ?? '180px' }"
        @keyup.enter="handleEnter"
        @update:model-value="(value: string) => setField(field.prop, value)"
      />
    </el-form-item>

    <el-form-item>
      <el-button type="primary" :loading="props.loading" @click="emit('search')">查 询</el-button>
      <el-button @click="emit('reset')">重 置</el-button>
      <!-- 右侧扩展操作(如新增按钮) -->
      <slot name="actions" />
    </el-form-item>
  </el-form>
</template>

<style scoped lang="scss">
.base-search {
  padding: $spacing-md $spacing-md 0;
  background: $color-bg-card;
  border-radius: $radius-md;
  box-shadow: $shadow-card;
  margin-bottom: $spacing-md;

  :deep(.el-form-item) {
    margin-bottom: $spacing-md;
  }
}
</style>
