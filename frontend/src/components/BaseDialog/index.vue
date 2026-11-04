<script setup lang="ts">
/**
 * BaseDialog:基于 el-dialog 的二次封装
 * - v-model 控制显隐
 * - 统一确认 / 取消回调与 loading
 */
interface Props {
  /** 标题 */
  title: string;
  /** 宽度 */
  width?: string | number;
  /** 确认按钮 loading */
  loading?: boolean;
  /** 确认按钮文案 */
  confirmText?: string;
  /** 取消按钮文案 */
  cancelText?: string;
  /** 是否显示底部按钮 */
  showFooter?: boolean;
  /** 关闭时销毁内容 */
  destroyOnClose?: boolean;
  /** 点击遮罩关闭 */
  closeOnClickModal?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  width: '560px',
  loading: false,
  confirmText: '确 定',
  cancelText: '取 消',
  showFooter: true,
  destroyOnClose: true,
  closeOnClickModal: false,
});

const visible = defineModel<boolean>({ required: true });

const emit = defineEmits<{
  (e: 'confirm'): void;
  (e: 'cancel'): void;
  (e: 'closed'): void;
}>();

function handleCancel(): void {
  visible.value = false;
  emit('cancel');
}

function handleConfirm(): void {
  emit('confirm');
}

function handleClosed(): void {
  emit('closed');
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="props.title"
    :width="props.width"
    :close-on-click-modal="props.closeOnClickModal"
    :destroy-on-close="props.destroyOnClose"
    append-to-body
    class="base-dialog"
    @closed="handleClosed"
  >
    <div class="base-dialog__body">
      <slot />
    </div>

    <template v-if="props.showFooter" #footer>
      <div class="base-dialog__footer">
        <slot name="footer">
          <el-button @click="handleCancel">{{ props.cancelText }}</el-button>
          <el-button type="primary" :loading="props.loading" @click="handleConfirm">
            {{ props.confirmText }}
          </el-button>
        </slot>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.base-dialog {
  &__body {
    max-height: 62vh;
    overflow-y: auto;
    padding-right: 4px;
    @include scrollbar;
  }

  &__footer {
    display: flex;
    justify-content: flex-end;
    gap: $spacing-sm;
  }
}
</style>
