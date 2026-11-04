<script lang="ts" setup>
import { ref } from 'vue';

import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import DataErrorBanner from '#/components/data-state/DataErrorBanner.vue';
import DataState from '#/components/data-state/DataState.vue';
import TablePagination from '#/components/table-pagination/TablePagination.vue';

/**
 * 列表页统一骨架（「页面即配置」）：
 * 页头（含刷新）→ 可折叠筛选卡（#filters + #actions）→ 表格卡（错误条 + el-table/自定义 #body + 空错加载态 + 分页）→ #append（弹窗等）。
 *
 * 状态由 useListPage 提供；本组件只负责结构与一致行为。
 * 默认插槽放 el-table-column；非表格列表（如卡片审核队列）用 #body 替代。
 */
withDefaults(
  defineProps<{
    /** 页头标题 */
    title: string;
    /** 页头描述 */
    description: string;
    /** 页头眉题，默认「织愈 · 业务管理」 */
    eyebrow?: string;
    /** 列表数据（表格模式必传） */
    data?: any[];
    loading?: boolean;
    /** 加载错误信息；有数据时显示错误条，空态时进空态组件 */
    error?: string;
    total?: number;
    emptyText?: string;
    emptyHint?: string;
    /** 分页是否显示每页条数选择，默认 true */
    showSizes?: boolean;
    /** 筛选区是否可折叠，默认 true */
    filtersCollapsible?: boolean;
    /** 禁用分页（如行级操作进行中），默认 false */
    paginationDisabled?: boolean;
    /** el-table 的 row-class-name 透传（如下钻定位高亮行） */
    rowClassName?: (params: { row: any; rowIndex: number }) => string;
  }>(),
  {
    eyebrow: undefined,
    data: () => [],
    loading: false,
    error: '',
    total: 0,
    emptyText: '暂无数据',
    emptyHint: '',
    showSizes: true,
    filtersCollapsible: true,
    paginationDisabled: false,
    rowClassName: undefined,
  },
);

const page = defineModel<number>('page', { default: 1 });
const pageSize = defineModel<number>('pageSize', { default: 20 });

const emit = defineEmits<{
  refresh: [];
  pageChange: [page: number];
}>();

const filtersVisible = ref(true);
</script>

<template>
  <div class="zy-business-page flex flex-col gap-4">
    <BusinessPageHeader
      class="zy-slide-up"
      :title="title"
      :description="description"
      :eyebrow="eyebrow"
      :loading="loading"
      @refresh="emit('refresh')"
    >
      <template v-if="$slots.headerActions" #default>
        <slot name="headerActions" />
      </template>
    </BusinessPageHeader>

    <!-- 页头下的横幅位（流程指引、公告等） -->
    <div v-if="$slots.banner" class="zy-slide-up zy-stagger" style="--stagger-i: 1">
      <slot name="banner" />
    </div>

    <!-- 筛选区（可折叠） -->
    <el-card
      v-if="$slots.filters || $slots.actions"
      class="zy-slide-up zy-stagger"
      style="--stagger-i: 2"
      shadow="never"
    >
      <div v-if="filtersCollapsible" class="zy-filter-heading">
        <span>筛选条件</span>
        <el-button
          link
          type="primary"
          :aria-expanded="filtersVisible"
          @click="filtersVisible = !filtersVisible"
        >
          {{ filtersVisible ? '收起筛选' : '展开筛选' }}
        </el-button>
      </div>
      <div
        v-show="!filtersCollapsible || filtersVisible"
        class="zy-filter-content flex flex-wrap items-center gap-3"
      >
        <slot name="filters" />
        <slot name="actions" />
      </div>
    </el-card>

    <!-- 列表区 -->
    <el-card class="zy-slide-up zy-stagger" style="--stagger-i: 3" shadow="never">
      <DataErrorBanner
        v-if="error && total > 0"
        :error="error"
        :retrying="loading"
        @retry="emit('refresh')"
      />

      <slot v-if="$slots.body" name="body" />
      <!-- 空数据首载走 DataState 骨架；有数据时的刷新才用表格遮罩，避免裸 spinner 盖空屏 -->
      <el-table
        v-else
        v-loading="loading && data.length > 0"
        :data="data"
        :row-class-name="rowClassName"
        style="width: 100%"
      >
        <template #empty>
          <slot name="empty">
            <DataState
              :loading="loading"
              :error="error"
              :empty-text="emptyText"
              :empty-hint="emptyHint"
              @retry="emit('refresh')"
            />
          </slot>
        </template>
        <slot />
      </el-table>

      <TablePagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :show-sizes="showSizes"
        :total="total"
        :disabled="paginationDisabled"
        @change="(p: number) => emit('pageChange', p)"
      />
    </el-card>

    <!-- 弹窗、抽屉等页级附属内容 -->
    <slot name="append" />
  </div>
</template>
