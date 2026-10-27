<script lang="ts" setup>
withDefaults(
  defineProps<{
    page: number;
    pageSize: number;
    pageSizes?: number[];
    showSizes?: boolean;
    total: number;
  }>(),
  {
    pageSizes: () => [10, 20, 50, 100],
    showSizes: false,
  },
);

const emit = defineEmits<{
  'update:page': [value: number];
  'update:pageSize': [value: number];
  change: [page: number];
}>();

function handlePage(value: number) {
  emit('update:page', value);
  emit('change', value);
}

function handleSize(value: number) {
  emit('update:pageSize', value);
  emit('update:page', 1);
  emit('change', 1);
}
</script>

<template>
  <el-pagination
    v-if="total > 0"
    class="mt-4 flex justify-end"
    :current-page="page"
    :layout="
      showSizes
        ? 'total, sizes, prev, pager, next, jumper'
        : 'total, prev, pager, next, jumper'
    "
    :page-size="pageSize"
    :page-sizes="pageSizes"
    :total="total"
    @update:current-page="handlePage"
    @update:page-size="handleSize"
  />
</template>
