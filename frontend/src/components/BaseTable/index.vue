<script setup lang="ts" generic="T">
import { computed } from 'vue';
import type { TableColumn } from '@/types';

/**
 * BaseTable:基于 el-table 的二次封装
 * - columns 配置式渲染
 * - 内置分页、loading、空数据
 * - 支持插槽扩展(列自定义内容)
 */
interface Props {
  /** 表格数据 */
  data: T[];
  /** 列配置 */
  columns: TableColumn<T>[];
  /** 加载状态 */
  loading?: boolean;
  /** 总条数 */
  total?: number;
  /** 当前页码 */
  pageNum?: number;
  /** 每页条数 */
  pageSize?: number;
  /** 是否显示分页 */
  showPagination?: boolean;
  /** 每页条数选项 */
  pageSizes?: number[];
  /** 行唯一键 */
  rowKey?: string;
  /** 斑马纹 */
  stripe?: boolean;
  /** 边框 */
  border?: boolean;
  /** 空数据文案 */
  emptyText?: string;
  /** 表格高度 */
  height?: string | number;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  total: 0,
  pageNum: 1,
  pageSize: 10,
  showPagination: true,
  pageSizes: () => [10, 20, 50, 100],
  rowKey: 'id',
  stripe: true,
  border: false,
  emptyText: '暂无数据',
  height: undefined,
});

const emit = defineEmits<{
  (e: 'update:pageNum', value: number): void;
  (e: 'update:pageSize', value: number): void;
  (e: 'selection-change', rows: T[]): void;
  (e: 'row-click', row: T): void;
  (e: 'sort-change', payload: { prop: string; order: string | null }): void;
}>();

/**
 * el-table 的 data 要求 DefaultRow[](带索引签名的类型),
 * 这里做一次安全的类型收窄,避免泛型 T 无法直接赋值
 */
const tableData = computed<Record<string, unknown>[]>(
  () => props.data as unknown as Record<string, unknown>[],
);

/**
 * 过滤掉自定义 slot 字段,其余属性透传给 el-table-column
 */
function columnAttrs(column: TableColumn<T>): Record<string, unknown> {
  const attrs: Record<string, unknown> = { ...column };
  delete attrs.slot;
  return attrs;
}

function handleCurrentChange(page: number): void {
  emit('update:pageNum', page);
}

function handleSizeChange(size: number): void {
  emit('update:pageSize', size);
}

function handleSelectionChange(rows: Record<string, unknown>[]): void {
  emit('selection-change', rows as unknown as T[]);
}

function handleSortChange(payload: { column: unknown; prop: string | null; order: string | null }): void {
  emit('sort-change', { prop: payload.prop ?? '', order: payload.order });
}
</script>

<template>
  <div class="base-table">
    <el-table
      v-loading="props.loading"
      :data="tableData"
      :row-key="props.rowKey"
      :stripe="props.stripe"
      :border="props.border"
      :height="props.height"
      :empty-text="props.emptyText"
      header-cell-class-name="base-table__header"
      @selection-change="handleSelectionChange"
      @row-click="(row: Record<string, unknown>) => emit('row-click', row as unknown as T)"
      @sort-change="handleSortChange"
    >
      <el-table-column
        v-for="(column, index) in props.columns"
        :key="`${column.prop ?? column.type ?? 'col'}-${index}`"
        v-bind="columnAttrs(column)"
      >
        <template v-if="column.slot" #default="scope">
          <!-- 列自定义插槽:slot 名由 columns 中的 slot 字段指定 -->
          <slot
            :name="column.slot ?? ''"
            :row="scope.row as T"
            :index="scope.$index as number"
            :column="column"
          />
        </template>
      </el-table-column>

      <!-- 追加到表格末尾的操作列等 -->
      <slot name="append" />
    </el-table>

    <div v-if="props.showPagination" class="base-table__pagination">
      <el-pagination
        background
        :current-page="props.pageNum"
        :page-size="props.pageSize"
        :page-sizes="props.pageSizes"
        :total="props.total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handleCurrentChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.base-table {
  width: 100%;

  &__pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: $spacing-md;
  }

  :deep(.base-table__header) {
    background-color: #f7f9fc !important;
    color: $color-text-primary;
    font-weight: 600;
  }
}
</style>
