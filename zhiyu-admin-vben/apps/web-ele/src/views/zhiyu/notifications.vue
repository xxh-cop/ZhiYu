<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter, type RouteLocationRaw } from 'vue-router';

import { ElMessage } from 'element-plus';

import { useUserStore } from '@vben/stores';

import {
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationApi,
} from '#/api/zhiyu/notification';
import ZyListPage from '#/components/zy-list-page/ZyListPage.vue';
import { useListPage } from '#/components/zy-list-page/useListPage';
import DataState from '#/components/data-state/DataState.vue';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import {
  NOTIFICATION_REF_TYPE,
  NOTIFICATION_TYPE,
  notificationTypeTag,
} from '#/constants/status-dict';
import { useNotificationStore } from '#/store';

/**
 * 通知中心：审核 / 预约 / 小组 / 关怀事件触达。
 * - 未读数有 5 秒服务端缓存，本页不承诺实时推送；
 * - 点击条目跳转到对应业务页并自动标记该条已读；
 * - 关怀类正文为脱敏引导语，不展示学生身份信息（不是数据缺失）。
 */
const router = useRouter();
const notificationStore = useNotificationStore();

/** 当前用户是否管理员（BH2：作品类通知落点按接收者角色分流） */
const userStore = useUserStore();
const isAdmin = computed(() =>
  (userStore.userInfo?.roles ?? []).includes('ROLE_3'),
);

const typeOptions = [
  { value: '', label: '全部类型' },
  ...Object.entries(NOTIFICATION_TYPE).map(([value, meta]) => ({
    value,
    label: meta.label,
  })),
];
const filterType = ref('');

/**
 * 关联对象筛选（BK，后端 BJ）：与 type 叠加生效，total 与全部筛选条件一致。
 * 「全部」= 不传参（filterRefType='' 时 fetcher 不带该参数，不传空串）。
 * 不收敛 type→refType 组合选项：组合关系归后端所有（BG 类批次持续扩展），
 * 前端收敛映射是重复声明会腐化；矛盾组合（如 WORK_AUDIT×booking）由空态文案兜住。
 */
const refTypeOptions = [
  { value: '', label: '全部对象' },
  ...Object.entries(NOTIFICATION_REF_TYPE).map(([value, meta]) => ({
    value,
    label: meta.label,
  })),
];
const filterRefType = ref('');

const {
  tableData,
  loading,
  errorMsg,
  total,
  currentPage,
  pageSize,
  fetchList,
  handleFilterChange,
  handlePageChange,
} = useListPage<NotificationApi.NotificationItem>({
  errorMessage: '通知列表加载失败，请重试。',
  fetcher: ({ page, pageSize: size }) => {
    const params: NotificationApi.ListParams = { page, size };
    if (filterType.value) params.type = filterType.value;
    if (filterRefType.value) params.refType = filterRefType.value;
    return getNotifications(params);
  },
});

function formatTime(dt?: string) {
  if (!dt) return '-';
  return String(dt).replace('T', ' ').slice(0, 16);
}

/** 空态文案（BK）：有筛选条件时说明是组合筛选无匹配，避免误读为「没有任何通知」 */
const hasFilter = computed(() => !!(filterType.value || filterRefType.value));
const emptyText = computed(() =>
  hasFilter.value ? '没有匹配的通知' : '暂无通知',
);
const emptyHint = computed(() =>
  hasFilter.value
    ? '当前类型 × 关联对象组合下没有通知；可调整或清空筛选条件'
    : '作品与评论审核、预约回复、小组与关怀事件会出现在这里；未读数有约 5 秒缓存，不必频繁刷新。',
);

const isCare = (item: NotificationApi.NotificationItem) => item.type === 'CARE_REMINDER';

/**
 * 按 refType 的业务落点；未知类型返回 null（只标记已读不跳转）。
 * work：接收者=作品作者，角色不限（学生端发作品无角色限制）。
 *   - admin（role=3）落 /zhiyu/business/works?workId= 直达作品详情抽屉（M1）；
 *   - 非 admin 接收者（传承人等）在管理端没有可达的作品页（works 页 ROLE_3 专属，
 *     不为落点硬放宽白名单），返回 null 只标已读、不显示跳转链接。
 *   - 学生的作品通知在其本人账号下，学生（role=0）本就被管理端守卫拒绝，
 *     不存在「学生通知错位出现在管理端」的问题。
 * project（BH）：项目审核结果通知（WORK_AUDIT + refType=project）只发给项目所属
 *   传承人（role=1）；admin 的项目审核页 /zhiyu/business/audit 是 ROLE_3 专属、
 *   对接收者不可达，故落点为传承人有权限的「我的项目」列表。该列表服务端分页、
 *   接口无 id 过滤参数，无法按 id 定位（不做假定位），由接收人在列表按状态自查。
 * comment：refId 是评论所属作品 ID（S2 起），落评论审核页。
 */
function targetFor(item: NotificationApi.NotificationItem): RouteLocationRaw | null {
  const id = item.refId; // 雪花 ID 字符串，原样传递
  switch (item.refType) {
    case 'booking': {
      return { path: '/zhiyu/programs', query: { bookingId: id } };
    }
    case 'care_reminder': {
      return { path: '/zhiyu/business/care-reminders', query: { reminderId: id } };
    }
    case 'comment': {
      return { path: '/zhiyu/comments', query: { commentId: id } };
    }
    case 'group': {
      return { path: '/zhiyu/business/groups', query: { groupId: id } };
    }
    case 'project': {
      return { path: '/my-inheritance/projects' };
    }
    case 'work': {
      return isAdmin.value
        ? { path: '/zhiyu/business/works', query: { workId: id } }
        : null;
    }
    default: {
      return null;
    }
  }
}

const targetLabels: Record<string, string> = {
  booking: '查看预约',
  care_reminder: '去关怀工作台',
  comment: '去评论审核页',
  group: '查看小组',
  project: '查看我的项目',
  work: '查看作品',
};

/** 标记单条已读（幂等）；成功后本地减未读数 */
const busyId = ref('');
async function markRead(item: NotificationApi.NotificationItem) {
  if (item.read || busyId.value) return;
  busyId.value = item.id;
  try {
    await markNotificationRead(item.id);
    item.read = true;
    notificationStore.decrease();
  } catch {
    // 拦截器已提示；1003/1004 时不改本地状态
  } finally {
    busyId.value = '';
  }
}

/** 点击条目：先标记已读，再跳转业务落点 */
async function openTarget(item: NotificationApi.NotificationItem) {
  const target = targetFor(item);
  if (!target) {
    await markRead(item);
    return;
  }
  await markRead(item);
  router.push(target);
}

/** 全部已读（幂等） */
const readingAll = ref(false);
async function readAll() {
  if (readingAll.value) return;
  readingAll.value = true;
  try {
    const res = await markAllNotificationsRead();
    notificationStore.clear();
    ElMessage.success(`已将 ${Number(res.updated ?? 0)} 条未读标记为已读`);
    await fetchList();
  } catch {
    // 拦截器已提示
  } finally {
    readingAll.value = false;
  }
}

const hasUnread = computed(() => tableData.value.some((n) => !n.read));
</script>

<template>
  <ZyListPage
    v-model:page="currentPage"
    v-model:page-size="pageSize"
    title="通知中心"
    description="审核结果、预约回复、小组动态与关怀提醒汇聚于此。点击条目跳转处理现场。"
    :data="tableData"
    :loading="loading"
    :error="errorMsg"
    :total="total"
    :empty-text="emptyText"
    :empty-hint="emptyHint"
    @refresh="fetchList"
    @page-change="handlePageChange"
  >
    <template #headerActions>
      <el-button
        type="primary"
        plain
        :loading="readingAll"
        :disabled="!hasUnread"
        @click="readAll"
      >
        全部标为已读
      </el-button>
    </template>

    <template #filters>
      <label for="notification-type" class="text-sm">类型</label>
      <el-select
        id="notification-type"
        v-model="filterType"
        placeholder="全部类型"
        style="width: 160px"
        @change="handleFilterChange"
      >
        <el-option
          v-for="opt in typeOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <label for="notification-ref-type" class="text-sm">关联对象</label>
      <el-select
        id="notification-ref-type"
        v-model="filterRefType"
        placeholder="全部对象"
        style="width: 170px"
        @change="handleFilterChange"
      >
        <el-option
          v-for="opt in refTypeOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </template>

    <template #body>
      <!-- 自定义列表态：加载骨架 / 空态与错误（含重试）/ 数据列表 -->
      <el-skeleton v-if="loading && !tableData.length" :rows="5" animated />
      <DataState
        v-else-if="!tableData.length"
        :loading="loading"
        :error="errorMsg"
        :empty-text="emptyText"
        :empty-hint="emptyHint"
        @retry="fetchList"
      />
      <ul v-else class="notification-list" aria-label="通知列表">
        <li
          v-for="item in tableData"
          :key="item.id"
          class="notification-item"
          :class="{ unread: !item.read }"
        >
          <button
            type="button"
            class="notification-main"
            :aria-label="`${item.read ? '已读' : '未读'}通知：${item.title}`"
            @click="openTarget(item)"
          >
            <span class="notification-side">
              <span v-if="!item.read" class="unread-dot" aria-hidden="true"></span>
              <ZyStatusTag v-bind="notificationTypeTag(item.type, item.refType)" />
            </span>
            <span class="notification-text">
              <span class="notification-title">{{ item.title }}</span>
              <span class="notification-content">{{ item.content }}</span>
              <span class="notification-meta">
                {{ formatTime(item.createTime) }}
                <template v-if="isCare(item)">
                  · 关怀类通知不展示学生身份信息，详情请走授权流程
                </template>
              </span>
            </span>
            <span v-if="targetFor(item)" class="notification-go">
              {{ targetLabels[item.refType] || '查看' }} →
            </span>
          </button>
          <el-button
            v-if="!item.read"
            class="notification-mark"
            size="small"
            text
            :loading="busyId === item.id"
            @click.stop="markRead(item)"
          >
            标记已读
          </el-button>
        </li>
      </ul>
    </template>
  </ZyListPage>
</template>

<style scoped>
.notification-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.notification-item {
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.notification-item:last-child {
  border-bottom: none;
}
.notification-main {
  flex: 1;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 8px;
  background: none;
  border: none;
  cursor: pointer;
  text-align: left;
  border-radius: 8px;
  transition: background-color 160ms ease;
}
.notification-main:hover {
  background-color: var(--el-fill-color-light);
}
.notification-main:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -2px;
}
.notification-side {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding-top: 2px;
  min-width: 96px;
}
.unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-danger);
  flex-shrink: 0;
}
.notification-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.notification-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}
.unread .notification-title {
  font-weight: 650;
  color: var(--el-text-color-primary);
}
.notification-content {
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  word-break: break-word;
}
.notification-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.notification-go {
  flex-shrink: 0;
  align-self: center;
  font-size: 12px;
  /* AP：暗色下品牌 600 深档仅 3.38:1，取对比方向色板（亮更深、暗更浅） */
  color: var(--el-color-primary-dark-2);
  white-space: nowrap;
}
.notification-mark {
  flex-shrink: 0;
  margin-right: 8px;
}
@media (prefers-reduced-motion: reduce) {
  .notification-main {
    transition: none;
  }
}
</style>
