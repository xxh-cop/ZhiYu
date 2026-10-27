<script lang="ts" setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';

const online = ref(true);

function sync() {
  online.value = navigator.onLine;
}

onMounted(() => {
  window.addEventListener('online', sync);
  window.addEventListener('offline', sync);
  sync();
});

onBeforeUnmount(() => {
  window.removeEventListener('online', sync);
  window.removeEventListener('offline', sync);
});
</script>

<template>
  <div v-show="!online" class="offline-banner" role="status" aria-live="polite" aria-atomic="true">
    网络已断开，部分内容可能无法加载。网络恢复后请重试。
  </div>
</template>

<style scoped>
.offline-banner {
  position: fixed;
  bottom: 20px;
  left: 50%;
  z-index: 1999;
  width: max-content;
  max-width: calc(100vw - 32px);
  padding: 10px 16px;
  font-size: 13px;
  color: var(--el-text-color-primary);
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: var(--el-border-radius-base);
  box-shadow: var(--el-box-shadow-light);
  pointer-events: none;
  transform: translateX(-50%);
}
</style>
