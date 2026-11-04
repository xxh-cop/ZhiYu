<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';

import { createIconifyIcon } from '@vben/icons';

import { useNotificationStore } from '#/store';

/**
 * 顶栏通知铃铛：未读数徽标 + 轮询入口。
 * 轮询生命周期由本组件挂载驱动（布局常驻 → 登录后始终在轮）；
 * 60s 间隔 + 页面隐藏暂停，语义对齐服务端 5 秒未读缓存。
 */
const BellIcon = createIconifyIcon('lucide:bell');

const router = useRouter();
const notificationStore = useNotificationStore();

const badgeText = computed(() =>
  notificationStore.unread > 99 ? '99+' : String(notificationStore.unread),
);

onMounted(() => notificationStore.startPolling());
onUnmounted(() => notificationStore.stopPolling());

function open() {
  router.push('/zhiyu/notifications');
}
</script>

<template>
  <button
    type="button"
    class="notification-bell"
    :aria-label="
      notificationStore.unread > 0
        ? `通知中心，${notificationStore.unread} 条未读`
        : '通知中心，无未读'
    "
    @click="open"
  >
    <BellIcon class="notification-bell__icon" />
    <span v-if="notificationStore.unread > 0" class="notification-bell__badge">
      {{ badgeText }}
    </span>
  </button>
</template>

<style scoped>
.notification-bell {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: hsl(var(--foreground));
  cursor: pointer;
  transition: background-color 160ms ease;
}
.notification-bell:hover {
  background: hsl(var(--accent));
}
.notification-bell:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 1px;
}
.notification-bell__icon {
  width: 18px;
  height: 18px;
}
.notification-bell__badge {
  position: absolute;
  top: -2px;
  right: -6px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--el-color-danger);
  color: var(--el-color-white);
  font-size: 10px;
  line-height: 16px;
  text-align: center;
  pointer-events: none;
}
@media (prefers-reduced-motion: reduce) {
  .notification-bell {
    transition: none;
  }
}
</style>
