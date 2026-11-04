<script setup lang="ts">
import { ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import { getWorkDetail, updateWorkStatus } from '#/api/zhiyu/admin-work';
import { requestClient } from '#/api/request';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import {
  COMMENT_STATUS,
  dictLabel,
  WORK_STATUS,
  WORK_VISIBLE,
} from '#/constants/status-dict';

import VideoPreview from './VideoPreview.vue';

/**
 * 作品详情抽屉（M1）：基本信息 + 授权媒体预览 + AI 寄语 + 评论区 + 对象级 AI 审计摘要 + 审核操作。
 *
 * 后端口径（不伪造）：
 * - 详情走 GET /admin/works/{id}（coverUrl 已由服务端签名；videoUrl 为私有引用，
 *   经 VideoPreview 走 /media/{id}/playback 签名播放，管理员角色后端放行，不绕过授权）。
 * - AI 审计摘要只覆盖「待审队列」：/admin/audit-queue?type=work 按 ref 精确匹配（I2 口径），
 *   最多翻 3 页（30 条）；匹配不到或非待审作品时显示与后端一致的说明语义，不显示成「无风险」。
 * - 评论区（AL2 2026-11-04 起）：O2 已交付管理端按作品过滤 GET /admin/comments?workId=&status=，
 *   三状态各取第一页合并按时间倒序；超出 20 条的状态提示去评论审核页看全量。
 * - 最近审核理由（auditReason）：WorkDetailVO 已回填（I1/AL2），非空才展示。
 */
const props = defineProps<{
  /** 雪花 ID 字符串原样传入；null 表示关闭 */
  workId: null | string;
}>();
const emit = defineEmits<{
  close: [];
  /** 审核动作完成后通知父级刷新列表 */
  reviewed: [];
}>();

const visible = ref(false);
const loading = ref(false);
const error = ref('');
const detail = ref<any>(null);

/** ── AI 审计摘要 ── */
const auditsLoading = ref(false);
const aiAudits = ref<any[]>([]);
const auditHint = ref('');

/** ── 评论区 ── */
const commentsLoading = ref(false);
const comments = ref<any[]>([]);
const commentsTotal = ref(0);
const commentsNote = ref('');

/** ── 审核操作 ── */
const busy = ref(false);

let loadTurn = 0;

function formatTime(dt?: string) {
  if (!dt) return '-';
  return String(dt).replace('T', ' ').slice(0, 19);
}

/** 待审队列按 ref 精确匹配对象级审计摘要（最多 3 页，防深翻页） */
async function loadAiAudits(id: string, turn: number) {
  auditsLoading.value = true;
  aiAudits.value = [];
  auditHint.value = '';
  try {
    for (let page = 1; page <= 3; page++) {
      const res = await requestClient.get('/admin/audit-queue', {
        params: { type: 'work', page },
      });
      if (turn !== loadTurn) return;
      const records: any[] = res.records || [];
      const hit = records.find((item) => String(item.id) === String(id));
      if (hit) {
        aiAudits.value = hit.aiAudits || [];
        auditHint.value = hit.auditHint || '';
        return;
      }
      if (records.length < 10) break; // 队列页大小固定 10，不足即到底
    }
    auditHint.value =
      '未在待审队列前 30 条内匹配到该作品的对象级审计摘要；这不代表未做安全过滤（2026-11-04 前的历史审计未记录对象关联）。';
  } catch {
    if (turn !== loadTurn) return;
    auditHint.value = 'AI 审计摘要加载失败，可在 AI 治理页按时间排查。';
  } finally {
    if (turn === loadTurn) auditsLoading.value = false;
  }
}

/** 评论区（AL2）：O2 管理端按作品过滤，三状态各取第一页合并倒序，状态用标签区分 */
async function loadComments(id: string, turn: number) {
  comments.value = [];
  commentsTotal.value = 0;
  commentsNote.value = '';
  commentsLoading.value = true;
  try {
    const [pending, published, hidden] = await Promise.all([
      requestClient.get('/admin/comments', { params: { page: 1, status: 0, workId: id } }),
      requestClient.get('/admin/comments', { params: { page: 1, status: 1, workId: id } }),
      requestClient.get('/admin/comments', { params: { page: 1, status: 2, workId: id } }),
    ]);
    if (turn !== loadTurn) return;
    const merged = [
      ...(pending.records || []),
      ...(published.records || []),
      ...(hidden.records || []),
    ].sort((a, b) => String(b.create_time).localeCompare(String(a.create_time)));
    comments.value = merged;
    commentsTotal.value =
      Number(pending.total ?? 0) + Number(published.total ?? 0) + Number(hidden.total ?? 0);
    if (!merged.length) {
      commentsNote.value = '该作品暂无评论。';
    } else if (commentsTotal.value > merged.length) {
      commentsNote.value = '仅显示各状态前 20 条；完整列表与处理见「评论审核」页。';
    }
  } catch {
    if (turn !== loadTurn) return;
    commentsNote.value = '评论区暂时无法读取，请稍后重试。';
  } finally {
    if (turn === loadTurn) commentsLoading.value = false;
  }
}

async function load(id: string) {
  const turn = ++loadTurn;
  loading.value = true;
  error.value = '';
  detail.value = null;
  aiAudits.value = [];
  auditHint.value = '';
  comments.value = [];
  commentsNote.value = '';
  try {
    const data = await getWorkDetail(id as any);
    if (turn !== loadTurn) return;
    detail.value = data;
    loading.value = false;
    if (Number(data.status) === 0) loadAiAudits(id, turn);
    loadComments(id, turn);
  } catch (e: any) {
    if (turn !== loadTurn) return;
    error.value = e?.message || '作品详情加载失败。';
    loading.value = false;
  }
}

watch(
  () => props.workId,
  (id) => {
    if (id) {
      visible.value = true;
      load(String(id));
    } else {
      visible.value = false;
      loadTurn++;
    }
  },
  { immediate: true },
);

function handleClosed() {
  loadTurn++;
  detail.value = null;
  emit('close');
}

/** 审核：通过（上架）/ 驳回下架（理由必填，服务端强制） */
async function review(status: number) {
  const work = detail.value;
  if (!work || busy.value) return;
  try {
    let reason: string | undefined;
    if (status === 2) {
      const { value } = await ElMessageBox.prompt(
        `下架后作者会看到该理由。请填写下架「${work.title}」的理由：`,
        '驳回并下架',
        {
          type: 'warning',
          confirmButtonText: '确认下架',
          inputPlaceholder: '必填，不超过 255 字',
          inputValidator: (v: string) => (v && v.trim() ? true : '请填写下架理由'),
          inputErrorMessage: '请填写下架理由',
        },
      );
      reason = value.trim();
    } else {
      await ElMessageBox.confirm(`确认通过并上架作品「${work.title}」？`, '通过作品', {
        type: 'info',
      });
    }
    busy.value = true;
    await updateWorkStatus(work.id, status, reason);
    ElMessage.success(status === 1 ? '已通过并上架' : '已下架');
    visible.value = false;
    emit('reviewed');
  } catch {
    // 用户取消或拦截器已提示
  } finally {
    busy.value = false;
  }
}

const AUDIT_ACTION_LABELS: Record<string, string> = {
  PASS: '通过',
  BLOCK: '拦截',
  REVIEW: '复核',
  REWRITE: '改写',
};
</script>

<template>
  <el-drawer
    v-model="visible"
    title="作品详情"
    size="min(560px, 100vw)"
    @closed="handleClosed"
  >
    <el-skeleton v-if="loading" :rows="6" animated />

    <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon>
      <template #default>
        <el-button size="small" @click="load(String(workId))">重试</el-button>
      </template>
    </el-alert>

    <template v-else-if="detail">
      <!-- 基本信息 -->
      <section class="drawer-section">
        <div class="work-head">
          <h3 class="work-title">{{ detail.title }}</h3>
          <ZyStatusTag :dict="WORK_STATUS" :value="detail.status" />
        </div>
        <dl class="meta-grid">
          <div><dt>作者</dt><dd>{{ detail.anonymous === 1 ? '匿名同学' : (detail.authorName || '-') }}</dd></div>
          <div><dt>媒体类型</dt><dd>{{ detail.mediaType || '-' }}</dd></div>
          <div><dt>可见性</dt><dd>{{ dictLabel(WORK_VISIBLE, detail.visible) }}</dd></div>
          <div><dt>点赞数</dt><dd>{{ detail.likeCount ?? 0 }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ formatTime(detail.createTime) }}</dd></div>
          <div><dt>作品 ID</dt><dd class="mono">{{ detail.id }}</dd></div>
          <div v-if="detail.auditReason" class="meta-wide">
            <dt>最近审核理由</dt>
            <dd>{{ detail.auditReason }}</dd>
          </div>
        </dl>
      </section>

      <!-- 媒体预览（私有媒体经签名播放，不绕过授权） -->
      <section v-if="detail.mediaType === 'video' && detail.videoUrl" class="drawer-section">
        <h4 class="section-title">创作视频</h4>
        <VideoPreview :src="detail.videoUrl" />
      </section>
      <section v-else-if="detail.coverUrl" class="drawer-section">
        <h4 class="section-title">封面</h4>
        <el-image
          :src="detail.coverUrl"
          :preview-src-list="[detail.coverUrl]"
          fit="cover"
          class="cover-image"
          preview-teleported
        />
      </section>

      <!-- AI 寄语（明文非敏感字段） -->
      <section v-if="detail.message" class="drawer-section">
        <h4 class="section-title">AI 寄语</h4>
        <p class="message-text">{{ detail.message }}</p>
      </section>

      <!-- 对象级 AI 审计摘要（脱敏；缺数据时说明语义，不说「无风险」） -->
      <section v-if="Number(detail.status) === 0" class="drawer-section">
        <h4 class="section-title">AI 审计摘要</h4>
        <el-skeleton v-if="auditsLoading" :rows="2" animated />
        <template v-else>
          <ul v-if="aiAudits.length" class="audit-list">
            <li v-for="(a, i) in aiAudits" :key="i">
              <span class="audit-scene">{{ a.scene }}</span>
              <span class="audit-action">{{ AUDIT_ACTION_LABELS[a.action] || a.action }}</span>
              <span class="audit-reason">{{ a.reason || '-' }}</span>
              <span class="audit-time">{{ formatTime(a.createTime ?? a.create_time) }}</span>
            </li>
          </ul>
          <p v-if="auditHint" class="audit-hint">{{ auditHint }}</p>
        </template>
      </section>

      <!-- 评论区（管理端按作品过滤，O2；三状态合并） -->
      <section class="drawer-section">
        <h4 class="section-title">
          评论区<template v-if="commentsTotal">（共 {{ commentsTotal }} 条）</template>
        </h4>
        <el-skeleton v-if="commentsLoading" :rows="3" animated />
        <template v-else>
          <ul v-if="comments.length" class="comment-list">
            <li v-for="c in comments" :key="c.id">
              <div class="comment-head">
                <ZyStatusTag :dict="COMMENT_STATUS" :value="c.status" />
                <span class="comment-meta">{{ c.nickname || '匿名' }} · {{ formatTime(c.create_time) }}</span>
              </div>
              <p class="comment-content">{{ c.content }}</p>
            </li>
          </ul>
          <p v-if="commentsNote" class="audit-hint">{{ commentsNote }}</p>
        </template>
      </section>

      <!-- 审核操作 -->
      <section class="drawer-section review-actions">
        <el-button
          v-if="Number(detail.status) !== 1"
          type="success"
          :loading="busy"
          @click="review(1)"
        >
          通过并上架
        </el-button>
        <el-button
          v-if="Number(detail.status) !== 2"
          type="danger"
          plain
          :disabled="busy"
          @click="review(2)"
        >
          驳回并下架（需理由）
        </el-button>
        <p class="audit-hint">驳回理由随审核落库并随通知送达作者。本系统不能替代专业心理咨询。</p>
      </section>
    </template>
  </el-drawer>
</template>

<style scoped>
.drawer-section {
  margin-bottom: 22px;
}
.work-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.work-title {
  margin: 0;
  font-size: 18px;
  font-weight: 650;
  line-height: 1.5;
  color: var(--el-text-color-primary);
}
.section-title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  margin: 0;
}
.meta-grid .meta-wide {
  grid-column: 1 / -1;
}
.meta-grid dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.meta-grid dd {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.mono {
  font-family: ui-monospace, monospace;
  font-size: 12px;
}
.cover-image {
  width: 200px;
  height: 200px;
  border-radius: 10px;
}
.message-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
}
.audit-list {
  list-style: none;
  margin: 0 0 8px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.audit-list li {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.audit-scene {
  font-weight: 600;
}
.audit-time {
  color: var(--el-text-color-secondary);
}
.audit-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}
.comment-list {
  list-style: none;
  margin: 0 0 8px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.comment-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.comment-content {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
  word-break: break-word;
}
.comment-meta {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.review-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding-top: 4px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.review-actions .audit-hint {
  flex-basis: 100%;
}
</style>
