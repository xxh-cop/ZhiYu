<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';

import { requestClient } from '#/api/request';
import DataState from '#/components/data-state/DataState.vue';
import ZyListPage from '#/components/zy-list-page/ZyListPage.vue';
import { useListPage } from '#/components/zy-list-page/useListPage';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import { COMMENT_STATUS, dictLabel } from '#/constants/status-dict';

interface Comment {
  id: string;
  content: string;
  status: number;
  nickname: string;
  workTitle: string;
  create_time: string;
}

/** 审核状态选项（文案取状态字典，保持 0/1/2 顺序） */
const labels = [0, 1, 2].map((v) => dictLabel(COMMENT_STATUS, v));

/** 筛选：审核状态 */
const status = ref(0);
/** 按作品过滤（O2 起后端支持 workId 参数；定位通知评论时使用） */
const workIdFilter = ref('');

/** 列表状态机（失败清空数据，宁空态不展示过期队列） */
const {
  tableData: rows,
  loading,
  errorMsg: error,
  total,
  currentPage: page,
  pageSize,
  fetchList: load,
  handleFilterChange: filter,
  handlePageChange,
} = useListPage<Comment>({
  clearOnError: true,
  errorMessage: '审核队列加载失败，请重试。',
  fetcher: ({ page: p }) =>
    requestClient.get('/admin/comments', {
      params: {
        page: p,
        status: status.value,
        ...(workIdFilter.value ? { workId: workIdFilter.value } : {}),
      },
    }),
});

/**
 * 通知中心落点：?commentId= 定位评论。
 * 先用 GET /admin/comments/{id}（O2 新增，返回 workId/status 等脱敏摘要）拿到归属，
 * 再按「该评论的状态 + 所属作品」过滤列表并高亮滚动到该条；
 * 评论已删除（1004）时给明确说明，不影响正常列表。
 */
const route = useRoute();
const locatedCommentId = ref('');
const locateNotice = ref('');

async function scrollToLocatedComment() {
  await nextTick();
  document
    .querySelector('.review-item.located')
    ?.scrollIntoView({ block: 'center', behavior: 'smooth' });
}

onMounted(async () => {
  const commentId = route.query.commentId;
  if (typeof commentId !== 'string' || !commentId) return;
  try {
    const detail = await requestClient.get(`/admin/comments/${commentId}`);
    locatedCommentId.value = String(commentId);
    status.value = Number(detail.status ?? 0);
    workIdFilter.value = String(detail.workId);
    page.value = 1;
    await load();
    if (rows.value.some((r) => String(r.id) === locatedCommentId.value)) {
      await scrollToLocatedComment();
      ElMessage.success('来自通知：已定位到该评论');
    } else {
      locateNotice.value = '该评论已定位到所属作品，但不在当前页（状态可能刚变化），可在状态页签间切换查看。';
    }
  } catch {
    locateNotice.value = '未找到该评论（可能已删除或撤回）。列表已按默认待审队列展示。';
  }
});

/** 手动切换状态页签时退出定位模式（恢复全量语义） */
function onStatusFilter() {
  workIdFilter.value = '';
  locatedCommentId.value = '';
  locateNotice.value = '';
  filter();
}

/** 行级审核 */
const busy = ref('');
const selected = ref<Comment | null>(null);
const detail = ref(false);

async function review(row: Comment, next: number) {
  if (busy.value) return;
  try {
    let reason: string | undefined;
    if (next === 2) {
      // I1（2026-11-04）起隐藏评论必须填写理由，落库并随通知送达作者
      const { value } = await ElMessageBox.prompt(
        '隐藏后仅评论作者可查看原文与状态。请填写隐藏理由：',
        '隐藏评论',
        {
          type: 'warning',
          confirmButtonText: '确认隐藏',
          inputPlaceholder: '必填，不超过 255 字',
          inputValidator: (v: string) => (v && v.trim() ? true : '请填写隐藏理由'),
          inputErrorMessage: '请填写隐藏理由',
        },
      );
      reason = value.trim();
    } else {
      await ElMessageBox.confirm('通过后其他用户可以看到这条评论。', '通过评论', {
        type: 'info',
      });
    }
    busy.value = row.id;
    await requestClient.post(`/admin/comments/${row.id}/review`, {
      status: next,
      expectedStatus: row.status,
      reason,
    });
    detail.value = false;
    ElMessage.success('审核结果已保存');
    await load();
    if (!rows.value.length && page.value > 1) {
      page.value--;
      await load();
    }
  } catch {
    // 用户取消或请求失败
  } finally {
    busy.value = '';
  }
}

/**
 * 评论导出已关闭（2026-11-04 用户决定）。
 * 评论导出含用户表达全文，批量外发需要「导出审计」四件齐备才允许重开：
 *   1. 操作留痕：admin_export_log（导出人/时间/筛选条件/条数）落库；
 *   2. 权限分离：导出权限独立于内容审核权限，需单独授权；
 *   3. 去标识：导出文件默认不含可识别身份字段（昵称脱敏）；
 *   4. 批量告警：单次导出超阈值触发告警。
 * 四件未就绪前本入口保持禁用，急用走人工审批（见页面说明文案）。
 * users / works 导出不受影响。
 */
const COMMENT_EXPORT_DISABLED = true;
</script>

<template>
  <ZyListPage
    v-model:page="page"
    v-model:page-size="pageSize"
    title="留暖评论审核"
    description="先阅读，再决定。保护表达的空间，也守住社区边界。"
    :loading="loading"
    :error="error"
    :total="total"
    :show-sizes="false"
    :filters-collapsible="false"
    :pagination-disabled="!!busy"
    class="review-page"
    @refresh="load"
    @page-change="handlePageChange"
  >
    <template #banner>
      <div class="review-guide">
        <span class="guide-mark">01 — 03</span>
        <div>
          <strong>阅读内容 → 判断公开范围 → 保存结果</strong>
          <p>新评论通过后才公开；作者可查看自己的审核状态并撤回。审核操作会留痕。</p>
        </div>
      </div>
      <el-alert
        v-if="locateNotice"
        :title="locateNotice"
        type="warning"
        :closable="false"
        show-icon
        style="margin-top: 12px"
      />
      <el-alert
        v-if="workIdFilter"
        title="正在按作品过滤评论（来自通知定位）"
        type="info"
        show-icon
        style="margin-top: 12px"
        @close="onStatusFilter"
      />
    </template>

    <template #filters>
      <el-radio-group v-model="status" :disabled="!!busy" @change="onStatusFilter">
        <el-radio-button v-for="(label, i) in labels" :key="i" :value="i">
          {{ label }}
        </el-radio-button>
      </el-radio-group>
    </template>
    <template #actions>
      <span class="export-disabled">
        <el-button :disabled="COMMENT_EXPORT_DISABLED">导出 CSV</el-button>
        <span class="export-note">
          评论导出已暂停：后端导出审计策略未放行评论导出（登记接口一律拒绝）。
          急用请走人工审批：由系统管理员在操作日志中登记导出人、范围与用途后线下导出。
        </span>
      </span>
      <span class="review-count">{{ loading ? '正在读取…' : `${total} 条评论` }}</span>
    </template>

    <template #body>
      <!-- 首载骨架 / 错误重试 / 具体空态：与 DataState 三态同一范式，不用裸 spinner -->
      <div class="review-list">
        <el-skeleton v-if="loading && !rows.length" :rows="4" animated />
        <DataState
          v-else-if="error && !rows.length"
          :error="error"
          @retry="load"
        />
        <el-empty
          v-else-if="!rows.length"
          :description="status === 0 ? '当前没有待审核评论' : '此分类暂无评论'"
        />
        <article
          v-for="row in rows"
          :key="row.id"
          class="review-item"
          :class="{ located: String(row.id) === locatedCommentId }"
        >
          <div class="item-meta">
            <span class="avatar">{{ row.nickname?.slice(0, 1) || '同' }}</span>
            <div>
              <strong>{{ row.nickname }}</strong>
              <p>
                {{ row.workTitle || '原作品已不可用' }} ·
                {{ String(row.create_time).replace('T', ' ').slice(0, 16) }}
              </p>
            </div>
            <ZyStatusTag :dict="COMMENT_STATUS" :value="row.status" />
          </div>
          <p class="comment-body">{{ row.content }}</p>
          <div class="item-actions">
            <el-button link type="primary" @click="selected = row; detail = true">
              展开全文
            </el-button>
            <div>
              <el-button v-if="row.status !== 2" :disabled="!!busy" @click="review(row, 2)">
                隐藏
              </el-button>
              <el-button
                v-if="row.status !== 1"
                type="primary"
                :loading="busy === row.id"
                :disabled="!!busy"
                @click="review(row, 1)"
              >
                通过并公开
              </el-button>
            </div>
          </div>
        </article>
      </div>
    </template>

    <template #append>
      <p class="review-foot">本系统不能替代专业心理咨询</p>
      <el-dialog v-model="detail" title="评论全文" width="min(640px, 94vw)">
        <template v-if="selected">
          <ZyStatusTag :dict="COMMENT_STATUS" :value="selected.status" />
          <p class="full-content">{{ selected.content }}</p>
          <p>所属作品：{{ selected.workTitle || '已不可用' }}</p>
        </template>
      </el-dialog>
    </template>
  </ZyListPage>
</template>

<style scoped>
.review-guide {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 24px;
  border: 1px solid var(--el-border-color);
  background: var(--el-color-primary-light-9);
  border-radius: 18px;
}
.guide-mark {
  color: var(--el-color-primary);
  font-size: 22px;
  white-space: nowrap;
}
.review-guide p {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin: 8px 0 0;
  line-height: 1.7;
}
.review-count {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-left: auto;
}
.export-disabled {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.export-note {
  max-width: 520px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
.review-list {
  min-height: 160px;
}
.review-item {
  padding: 24px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 16px;
  margin-bottom: 16px;
}
.review-item.located {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.item-meta {
  display: flex;
  gap: 12px;
  align-items: center;
}
.item-meta > div {
  flex: 1;
  min-width: 0;
}
.item-meta p {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin: 8px 0 0;
  line-height: 1.7;
}
.avatar {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--el-color-primary-light-9);
  /* AP：暗色下品牌 600 深档在 dark 底上仅 3.38:1，取对比方向色板 */
  color: var(--el-color-primary-dark-2);
}
.comment-body {
  line-height: 1.9;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 20px 0;
  color: var(--el-text-color-primary);
}
.item-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 16px;
}
.full-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.9;
}
.review-foot {
  text-align: center;
  color: var(--el-text-color-regular);
  font-size: 12px;
}
.review-list :deep(.el-empty__description p) {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
@media (max-width: 600px) {
  .review-item {
    padding: 16px;
  }
  .item-meta {
    flex-wrap: wrap;
  }
}
</style>
