<script lang="ts" setup>
import { computed } from 'vue';

import type { StatusDict } from '#/constants/status-dict';
import { dictLabel, dictTag } from '#/constants/status-dict';

/**
 * 状态标签统一渲染：颜色语义由 #/constants/status-dict 字典承载，
 * 页面不再各写 type="success" 硬映射。未知值原样展示（不猜）。
 */
const props = withDefaults(
  defineProps<{
    /** 状态字典（#/constants/status-dict 导出） */
    dict: StatusDict;
    /** 状态原始值 */
    value: number | string | undefined | null;
    /** 尺寸，默认 small（列表页统一） */
    size?: 'default' | 'large' | 'small';
    /** el-tag effect，默认light */
    effect?: 'dark' | 'light' | 'plain';
  }>(),
  {
    size: 'small',
    effect: 'light',
  },
);

const label = computed(() => dictLabel(props.dict, props.value));
const tagType = computed(() => dictTag(props.dict, props.value));
</script>

<template>
  <el-tag :type="tagType" :size="size" :effect="effect">{{ label }}</el-tag>
</template>
