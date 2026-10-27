<script lang="ts" setup>
import { useElementPlusDesignTokens } from '@vben/hooks';

import { ElConfigProvider } from 'element-plus';

import OfflineBanner from '#/components/offline-banner/OfflineBanner.vue';
import { elementLocale } from '#/locales';

defineOptions({ name: 'App' });

useElementPlusDesignTokens();
</script>

<template>
  <ElConfigProvider :locale="elementLocale">
    <OfflineBanner />
    <RouterView />
  </ElConfigProvider>
</template>
