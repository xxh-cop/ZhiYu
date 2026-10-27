<script lang="ts" setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';

import { useUserStore } from '@vben/stores';

import { ElButton, ElResult } from 'element-plus';

import { homePathForRoles } from '#/utils/home-path';

defineOptions({ name: 'NotFoundView' });

const router = useRouter();
const userStore = useUserStore();
const homePath = computed(() =>
  homePathForRoles(userStore.userInfo?.roles ?? []),
);
</script>

<template>
  <div class="fallback-page">
    <ElResult
      icon="info"
      title="页面不存在或已移动"
      sub-title="请检查链接是否完整；也可以返回工作台继续处理待办。"
    >
      <template #extra>
        <ElButton type="primary" @click="router.push(homePath)">
          返回工作台
        </ElButton>
      </template>
    </ElResult>
  </div>
</template>

<style scoped>
.fallback-page {
  display: grid;
  place-items: center;
  min-height: 60vh;
  padding: 48px 24px;
}
</style>
