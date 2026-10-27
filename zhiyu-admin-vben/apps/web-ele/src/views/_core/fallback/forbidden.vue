<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { ElButton, ElResult } from 'element-plus';

defineOptions({ name: 'ForbiddenView' });

const router = useRouter();
</script>

<template>
  <div class="fallback-page">
    <ElResult
      icon="warning"
      title="没有管理端访问权限"
      sub-title="当前账号无权访问该页面。学生账号请使用学生端；如需开通权限，请联系平台管理员。"
    >
      <template #extra>
        <ElButton type="primary" @click="router.push('/zhiyu/dashboard')">
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
