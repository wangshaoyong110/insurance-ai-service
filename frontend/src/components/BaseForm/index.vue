<script setup lang="ts">
import { computed, ref } from 'vue';
import type { FormInstance, FormItemRule, FormRules } from 'element-plus';
import type { FormField } from '@/types';

/**
 * BaseForm:基于 el-form 的二次封装
 * - schema(fields)驱动渲染
 * - 支持校验规则、栅格布局、自定义插槽
 * - 通过 defineExpose 暴露 validate / resetFields
 *
 * 说明:model 使用 object 类型以兼容各类业务接口类型(interface 无索引签名),
 *      内部通过 toRecord 统一按键读写。
 */
interface Props {
  /** 表单数据对象(父组件持有,内部直接修改字段) */
  model: object;
  /** 字段配置 */
  fields: FormField[];
  /** 校验规则 */
  rules?: FormRules | Record<string, FormItemRule[]>;
  /** 标签宽度 */
  labelWidth?: string;
  /** 是否行内表单 */
  inline?: boolean;
  /** 是否禁用整个表单 */
  disabled?: boolean;
  /** 提交按钮 loading */
  loading?: boolean;
  /** 是否显示操作按钮 */
  showActions?: boolean;
  /** 提交按钮文案 */
  submitText?: string;
  /** 重置按钮文案 */
  resetText?: string;
  /** 标签位置 */
  labelPosition?: 'left' | 'right' | 'top';
}

const props = withDefaults(defineProps<Props>(), {
  rules: () => ({}),
  labelWidth: '96px',
  inline: false,
  disabled: false,
  loading: false,
  showActions: true,
  submitText: '保 存',
  resetText: '重 置',
  labelPosition: 'right',
});

const emit = defineEmits<{
  (e: 'submit'): void;
  (e: 'reset'): void;
  (e: 'field-change', payload: { prop: string; value: unknown }): void;
}>();

const formRef = ref<FormInstance>();

/** 统一以键值对形式访问表单数据 */
const modelRecord = computed<Record<string, unknown>>(() => props.model as Record<string, unknown>);

function getValue(prop: string): unknown {
  return modelRecord.value[prop];
}

/** 写入字段值并通知父组件 */
function setField(prop: string, value: unknown): void {
  modelRecord.value[prop] = value;
  emit('field-change', { prop, value });
}

/** unknown -> string,用于 input / textarea */
function asString(value: unknown): string {
  return value === null || value === undefined ? '' : String(value);
}

/**
 * unknown -> 表单控件可接受的基础值
 * el-select / el-radio-group 的 modelValue 为联合类型,直接传 unknown 无法通过类型检查
 */
function asFieldValue(value: unknown): string | number | boolean | undefined {
  if (value === null || value === undefined) {
    return undefined;
  }
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return value;
  }
  return String(value);
}

/** unknown -> number | undefined,用于 el-input-number */
function asNumber(value: unknown): number | undefined {
  return typeof value === 'number' ? value : undefined;
}

/** unknown -> boolean,用于 el-switch */
function asBoolean(value: unknown): boolean {
  return value === true || value === 1 || value === '1';
}

/**
 * 校验表单,校验通过后触发 submit
 */
async function validate(): Promise<boolean> {
  if (!formRef.value) {
    return false;
  }
  try {
    await formRef.value.validate();
    emit('submit');
    return true;
  } catch {
    return false;
  }
}

/** 重置表单校验状态(不清空数据) */
function resetFields(): void {
  formRef.value?.clearValidate();
  emit('reset');
}

defineExpose({ validate, resetFields });
</script>

<template>
  <el-form
    ref="formRef"
    :model="props.model"
    :rules="props.rules"
    :label-width="props.labelWidth"
    :inline="props.inline"
    :disabled="props.disabled"
    :label-position="props.labelPosition"
    class="base-form"
    @submit.prevent
  >
    <el-row :gutter="16">
      <el-col v-for="field in props.fields" :key="field.prop" :span="field.span ?? 24">
        <el-form-item :label="field.label" :prop="field.prop">
          <!-- 自定义插槽字段 -->
          <slot
            v-if="field.type === 'slot' && field.slot"
            :name="field.slot"
            :model="props.model"
            :field="field"
          />

          <!-- 下拉选择 -->
          <el-select
            v-else-if="field.type === 'select'"
            :model-value="asFieldValue(getValue(field.prop))"
            :placeholder="field.placeholder ?? `请选择${field.label}`"
            :disabled="field.disabled"
            :clearable="field.clearable ?? true"
            class="base-form__control"
            v-bind="field.props"
            @update:model-value="(value: unknown) => setField(field.prop, value)"
          >
            <el-option
              v-for="option in field.options ?? []"
              :key="String(option.value)"
              :label="option.label"
              :value="option.value"
            />
          </el-select>

          <!-- 单选 -->
          <el-radio-group
            v-else-if="field.type === 'radio'"
            :model-value="asFieldValue(getValue(field.prop))"
            :disabled="field.disabled"
            v-bind="field.props"
            @update:model-value="(value: unknown) => setField(field.prop, value)"
          >
            <el-radio
              v-for="option in field.options ?? []"
              :key="String(option.value)"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>

          <!-- 开关 -->
          <el-switch
            v-else-if="field.type === 'switch'"
            :model-value="asBoolean(getValue(field.prop))"
            :disabled="field.disabled"
            v-bind="field.props"
            @update:model-value="(value: unknown) => setField(field.prop, value)"
          />

          <!-- 数字 -->
          <el-input-number
            v-else-if="field.type === 'number'"
            :model-value="asNumber(getValue(field.prop))"
            :disabled="field.disabled"
            class="base-form__control"
            v-bind="field.props"
            @update:model-value="(value: number | undefined) => setField(field.prop, value)"
          />

          <!-- 日期 -->
          <el-date-picker
            v-else-if="field.type === 'date'"
            :model-value="getValue(field.prop) as string"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="field.placeholder ?? `请选择${field.label}`"
            :disabled="field.disabled"
            class="base-form__control"
            v-bind="field.props"
            @update:model-value="(value: unknown) => setField(field.prop, value)"
          />

          <!-- 多行文本 -->
          <el-input
            v-else-if="field.type === 'textarea'"
            :model-value="asString(getValue(field.prop))"
            type="textarea"
            :rows="field.rows ?? 4"
            :maxlength="field.maxlength"
            :placeholder="field.placeholder ?? `请输入${field.label}`"
            :disabled="field.disabled"
            show-word-limit
            v-bind="field.props"
            @update:model-value="(value: string) => setField(field.prop, value)"
          />

          <!-- 密码 -->
          <el-input
            v-else-if="field.type === 'password'"
            :model-value="asString(getValue(field.prop))"
            type="password"
            show-password
            :maxlength="field.maxlength"
            :placeholder="field.placeholder ?? `请输入${field.label}`"
            :disabled="field.disabled"
            v-bind="field.props"
            @update:model-value="(value: string) => setField(field.prop, value)"
          />

          <!-- 默认单行输入 -->
          <el-input
            v-else
            :model-value="asString(getValue(field.prop))"
            :maxlength="field.maxlength"
            :placeholder="field.placeholder ?? `请输入${field.label}`"
            :disabled="field.disabled"
            :clearable="field.clearable ?? true"
            v-bind="field.props"
            @update:model-value="(value: string) => setField(field.prop, value)"
          />
        </el-form-item>
      </el-col>

      <el-col v-if="props.showActions" :span="24">
        <div class="base-form__actions">
          <el-button type="primary" :loading="props.loading" @click="validate">
            {{ props.submitText }}
          </el-button>
          <el-button @click="resetFields">{{ props.resetText }}</el-button>
          <slot name="actions" />
        </div>
      </el-col>
    </el-row>
  </el-form>
</template>

<style scoped lang="scss">
.base-form {
  &__control {
    width: 100%;
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: $spacing-sm;
    padding-top: $spacing-sm;
  }
}
</style>
