<script lang="ts" setup>
import type { MenuRecordRaw } from '@vben/types';

import { computed } from 'vue';

import { GlobalSearch } from '@vben/layouts';
import { useAccessStore } from '@vben/stores';

import { useSearchRegistryStore } from '#/store/search-registry';

/**
 * 全局搜索入口（AB2，命令面板风格，Cmd/Ctrl+K 唤起）。
 * 复用框架内置 GlobalSearch 的交互（Esc 关闭 / 上下键选择 / Enter 确认 /
 * 搜索历史），通过覆盖其 menus 输入把「页面数据」实体并入候选：
 * - 菜单跳转：accessStore.accessMenus（按当前角色过滤后的菜单树）；
 * - 实体跳转：search-registry 中本会话已加载的作品 / 用户 / 小组，
 *   选中后跳对应列表页并带 query 定位；不查远端、不做模糊拼音。
 *
 * 内置面板只在挂载时读取一次 menus，实体注册表变化时以 :key 重建索引。
 */
const accessStore = useAccessStore();
const registry = useSearchRegistryStore();

const menus = computed<MenuRecordRaw[]>(() => {
  const base = (accessStore.accessMenus ?? []) as MenuRecordRaw[];
  return [...base, ...registry.asMenus] as MenuRecordRaw[];
});
</script>

<template>
  <GlobalSearch :key="registry.version" :menus="menus" class="mr-1 sm:mr-4" />
</template>
