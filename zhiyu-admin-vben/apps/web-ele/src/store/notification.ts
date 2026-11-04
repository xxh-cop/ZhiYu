import { ref } from 'vue';

import { defineStore } from 'pinia';

import { getUnreadCount } from '#/api/zhiyu/notification';

/**
 * 未读数共享状态与轮询控制。
 * 轮询语义对齐服务端 5 秒未读数缓存：60s 一轮足够，不做实时推送承诺；
 * 页面隐藏（document.hidden）时暂停，回前台立即补一次。
 * 标记已读后由调用方立刻本地减计数，不等下一轮。
 */
const POLL_INTERVAL_MS = 60_000;

export const useNotificationStore = defineStore('notification', () => {
  const unread = ref(0);

  let timer: ReturnType<typeof setTimeout> | undefined;
  let polling = false;
  let inFlight: Promise<void> | undefined;

  async function refreshUnread() {
    // 并发去重：轮询与手动刷新撞车时复用同一请求
    if (inFlight) return inFlight;
    inFlight = (async () => {
      try {
        const res = await getUnreadCount();
        unread.value = Math.max(0, Number(res.unread ?? 0));
      } catch {
        // 未读数拉取失败不打扰页面（拦截器已提示过的不重复处理）
      } finally {
        inFlight = undefined;
      }
    })();
    return inFlight;
  }

  function clearTimer() {
    if (timer !== undefined) {
      clearTimeout(timer);
      timer = undefined;
    }
  }

  /** 自愈式 setTimeout：一次请求落定后再排下一轮，避免慢请求叠加 */
  function schedule() {
    clearTimer();
    timer = setTimeout(async () => {
      await refreshUnread();
      if (polling && !document.hidden) schedule();
    }, POLL_INTERVAL_MS);
  }

  function onVisibilityChange() {
    if (document.hidden) {
      clearTimer();
    } else if (polling) {
      refreshUnread();
      schedule();
    }
  }

  function startPolling() {
    if (polling) return;
    polling = true;
    refreshUnread();
    if (!document.hidden) schedule();
    document.addEventListener('visibilitychange', onVisibilityChange);
  }

  function stopPolling() {
    polling = false;
    clearTimer();
    document.removeEventListener('visibilitychange', onVisibilityChange);
  }

  /** 标记已读后立即本地减计数 */
  function decrease(n = 1) {
    unread.value = Math.max(0, unread.value - n);
  }

  function clear() {
    unread.value = 0;
  }

  return {
    unread,
    refreshUnread,
    startPolling,
    stopPolling,
    decrease,
    clear,
  };
});
