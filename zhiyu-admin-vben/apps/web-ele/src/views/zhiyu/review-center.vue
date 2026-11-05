<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import { auditProject } from '#/api/zhiyu/admin';
import { getWorkList, updateWorkStatus } from '#/api/zhiyu/admin-work';
import { requestClient } from '#/api/request';
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import { WORK_VISIBLE, dictLabel } from '#/constants/status-dict';

import AiSuggestPanel from './components/AiSuggestPanel.vue';

/**
 * 审核工作台：聚合「评论 + 作品 + 非遗项目 + 校园核验」待审的统一队列式决策页。
 * 接口现状：
 * - 评论：GET /admin/comments?status=0；review {status, expectedStatus, reason}（乐观锁 + I1 起驳回必填理由）
 * - 作品：GET /admin/works?status=0；PUT /admin/works/{id}/status {status, reason}（I1 起驳回必填理由）
 * - 项目：GET /admin/project-catalog?status=0（固定 10 条/页）；PUT /admin/projects/{id}/audit
 *   {action: approve|reject, reason}（reject 服务端强制理由）
 * - 校园核验：GET /admin/campus-verifications?status=PENDING（AG1 起支持 status 过滤；
 *   不传为全状态混排，队列必须显式传 PENDING）；POST /admin/campus-verifications/{id}/review
 *   {decision: APPROVED|REJECTED, reason}（通过/驳回服务端都必填说明 → 校园类两个动作都走说明弹窗）
 * - 驳回理由随审核落库 audit_reason，并随通知送达作者（后端 I1，2026-11-04）。
 * - 四个接口均无批量端点 → 本页逐条处理，不做循环打点伪装批量。
 * - /admin/audit-queue 的 work/comment 类型后端未实现（返回空），本页不使用它。
 */

type QueueItem = {
  author: string;
  content: string;
  coverUrl?: string;
  createTime: string;
  id: string;
  kind: 'campus' | 'comment' | 'project' | 'work';
  title: string;
  visible?: number;
  /** 项目类：分类 / 级别 / 预计分钟（仅展示接口已返回字段，该接口无简介字段） */
  category?: string;
  level?: number;
  estMinutes?: number;
  /** 校园核验类：学校 / 学院（脱敏摘要；身份明细不在队列展示，注明去向） */
  school?: string;
  college?: string;
};

const KIND_LABELS = { campus: '校园核验', comment: '评论', project: '项目', work: '作品' } as const;

const queue = ref<QueueItem[]>([]);
const currentIndex = ref(0);
const loading = ref(false);
const error = ref('');
const busy = ref(false);
/** 本次会话已处理条数与每条耗时（非持久化，刷新清零；历史统计需后端支持，待建） */
const processedCount = ref(0);
const durations = ref<number[]>([]);
/** 当前条目进入视野的时间戳，决策时计入耗时 */
const itemShownAt = ref(Date.now());
/** 队列分页续取（页大小由服务端固定：评论/校园 20、作品 50、项目 10） */
const commentPage = ref(1);
const workPage = ref(1);
const projectPage = ref(1);
const campusPage = ref(1);
const commentTotal = ref(0);
const workTotal = ref(0);
const projectTotal = ref(0);
const campusTotal = ref(0);

const current = computed<QueueItem | null>(() => queue.value[currentIndex.value] ?? null);
const remaining = computed(() => queue.value.length);
const upcoming = computed(() => queue.value.slice(currentIndex.value + 1, currentIndex.value + 4));

/** 平均处理秒数（会话内口径：条目进入视野 → 决策成功） */
const avgSeconds = computed(() => {
  if (!durations.value.length) return null;
  const sum = durations.value.reduce((a, b) => a + b, 0);
  return Math.round((sum / durations.value.length / 1000) * 10) / 10;
});

/** 当前条目切换时重置计时起点 */
watch(
  () => (current.value ? `${current.value.kind}:${current.value.id}` : ''),
  (val, old) => {
    if (val && val !== old) itemShownAt.value = Date.now();
  },
);

function formatTime(dt?: string) {
  if (!dt) return '-';
  return String(dt).replace('T', ' ').slice(0, 16);
}

function mapComment(row: any): QueueItem {
  return {
    kind: 'comment',
    id: String(row.id),
    author: row.nickname || '匿名',
    title: row.workTitle || '原作品已不可用',
    content: row.content || '',
    createTime: row.create_time,
  };
}

function mapWork(row: any): QueueItem {
  return {
    kind: 'work',
    id: String(row.id),
    author: row.authorName || '匿名',
    title: row.title || '未命名作品',
    content: '',
    coverUrl: row.coverUrl,
    visible: row.visible,
    createTime: row.createTime,
  };
}

function mapProject(row: any): QueueItem {
  return {
    kind: 'project',
    id: String(row.id),
    author: '传承人',
    title: row.title || '未命名项目',
    content: '',
    category: row.category || '未分类',
    level: row.level,
    estMinutes: row.estMinutes,
    createTime: row.createTime,
  };
}

/** 校园核验：脱敏摘要（学校/学院/提交时间），身份明细不在队列展示 */
function mapCampus(row: any): QueueItem {
  return {
    kind: 'campus',
    id: String(row.id),
    author: '学生',
    title: `${row.school || '—'} · ${row.college || '—'}`,
    content: '',
    school: row.school,
    college: row.college,
    createTime: row.create_time,
  };
}

/** 四类时间格式不一（ISO 带 T / 空格分隔 / 毫秒尾），统一成可比较的文本再倒序 */
function normalizeTime(dt?: string) {
  return String(dt || '').replace('T', ' ').slice(0, 19);
}

/** 拉取四类待审（各自一页），按提交时间倒序合并 */
async function loadQueues() {
  loading.value = true;
  error.value = '';
  try {
    const [comments, works, projects, campuses] = await Promise.all([
      requestClient.get('/admin/comments', { params: { page: commentPage.value, status: 0 } }),
      getWorkList({ page: workPage.value, pageSize: 50, status: 0 }),
      requestClient.get('/admin/project-catalog', { params: { page: projectPage.value, status: 0 } }),
      requestClient.get('/admin/campus-verifications', { params: { page: campusPage.value, status: 'PENDING' } }),
    ]);
    commentTotal.value = Number(comments.total ?? 0);
    workTotal.value = Number(works.total ?? 0);
    projectTotal.value = Number(projects.total ?? 0);
    campusTotal.value = Number(campuses.total ?? 0);
    const items = [
      ...(comments.records || []).map(mapComment),
      ...(works.records || []).map(mapWork),
      ...(projects.records || []).map(mapProject),
      ...(campuses.records || []).map(mapCampus),
    ].sort((a, b) => normalizeTime(b.createTime).localeCompare(normalizeTime(a.createTime)));
    queue.value = items;
    currentIndex.value = 0;
  } catch (e: any) {
    error.value = e?.message || '审核队列加载失败，请重试。';
    queue.value = [];
  } finally {
    loading.value = false;
  }
}

/** 队列耗尽后尝试续取下一页 */
async function refillIfNeeded() {
  if (queue.value.length > 0) return;
  const moreComments = commentPage.value * 20 < commentTotal.value; // 评论页大小由服务端固定，按 20 保守估计续取
  const moreWorks = workPage.value * 50 < workTotal.value;
  const moreProjects = projectPage.value * 10 < projectTotal.value;
  const moreCampus = campusPage.value * 20 < campusTotal.value;
  if (!moreComments && !moreWorks && !moreProjects && !moreCampus) return;
  if (moreComments) commentPage.value += 1;
  if (moreWorks) workPage.value += 1;
  if (moreProjects) projectPage.value += 1;
  if (moreCampus) campusPage.value += 1;
  await loadQueues();
}

function advance() {
  queue.value.splice(currentIndex.value, 1);
  if (currentIndex.value >= queue.value.length) {
    currentIndex.value = Math.max(0, queue.value.length - 1);
  }
  if (queue.value.length === 0) refillIfNeeded();
}

async function decide(action: 'approve' | 'reject', reason?: string) {
  const item = current.value;
  if (!item || busy.value) return;
  // 校园核验服务端对通过/驳回都强制要求说明（reason @NotBlank）→ 两个动作都先走说明弹窗
  if (item.kind === 'campus' && !reason) {
    openReasonDialog(action);
    return;
  }
  busy.value = true;
  try {
    if (item.kind === 'comment') {
      await requestClient.post(`/admin/comments/${item.id}/review`, {
        status: action === 'approve' ? 1 : 2,
        expectedStatus: 0,
        reason,
      });
    } else if (item.kind === 'project') {
      await auditProject(item.id as any, { action, reason });
    } else if (item.kind === 'campus') {
      await requestClient.post(`/admin/campus-verifications/${item.id}/review`, {
        decision: action === 'approve' ? 'APPROVED' : 'REJECTED',
        reason,
      });
    } else {
      await updateWorkStatus(item.id as any, action === 'approve' ? 1 : 2, reason);
    }
    processedCount.value += 1;
    durations.value.push(Date.now() - itemShownAt.value);
    ElMessage.success(action === 'approve' ? '已通过' : '已驳回');
    advance();
  } catch {
    // 拦截器已提示；评论 expectedStatus 冲突时保持当前条目，可刷新重试
  } finally {
    busy.value = false;
  }
}

/** 理由弹窗（驳回必填；校园核验通过/驳回都必填说明；I1 起随审核结果落库并通知） */
const rejectVisible = ref(false);
const rejectReason = ref('');
const rejectError = ref('');
const pendingAction = ref<'approve' | 'reject'>('reject');

const reasonDialogTitle = computed(() =>
  pendingAction.value === 'approve' ? '通过说明（校园核验）' : '驳回理由',
);
const reasonDialogHint = computed(() =>
  pendingAction.value === 'approve'
    ? '请确认已通过学校认可的渠道核实身份，再填写说明。不要填写证件号等敏感内容。'
    : '理由必填，随审核结果落库（audit_reason）并随通知送达作者；请写得具体、克制。',
);

function openReasonDialog(action: 'approve' | 'reject') {
  if (!current.value || busy.value) return;
  pendingAction.value = action;
  rejectReason.value = '';
  rejectError.value = '';
  rejectVisible.value = true;
}

function openReject() {
  openReasonDialog('reject');
}

async function confirmReject() {
  if (!rejectReason.value.trim()) {
    rejectError.value = pendingAction.value === 'approve' ? '请填写通过说明' : '请填写驳回理由';
    return;
  }
  rejectVisible.value = false;
  await decide(pendingAction.value, rejectReason.value.trim());
}

function goNext() {
  if (currentIndex.value < queue.value.length - 1) currentIndex.value += 1;
}
function goPrev() {
  if (currentIndex.value > 0) currentIndex.value -= 1;
}

/** 快捷键帮助面板（? 唤起，Esc 关闭，关闭后焦点归还触发按钮） */
const helpVisible = ref(false);
const helpBtnRef = ref();

function openHelp() {
  helpVisible.value = true;
}
function onHelpClosed() {
  const el = (helpBtnRef.value as any)?.$el as HTMLElement | undefined;
  el?.focus();
}

/** 首次进入引导条（可关闭，localStorage 记忆） */
const guideVisible = ref(localStorage.getItem('reviewGuideDismissed') !== 'true');
function dismissGuide() {
  guideVisible.value = false;
  localStorage.setItem('reviewGuideDismissed', 'true');
}

/** 键盘快捷键：J/K 移动、P 通过、R 驳回、? 帮助；输入控件与弹窗打开时不触发 */
function isEditableTarget(target: EventTarget | null) {
  const el = target as HTMLElement | null;
  return !!el?.closest('input, textarea, select, [contenteditable="true"]');
}
function onKeydown(e: KeyboardEvent) {
  if (busy.value || rejectVisible.value || helpVisible.value || isEditableTarget(e.target)) return;
  const key = e.key.toLowerCase();
  if (key === 'j') goNext();
  else if (key === 'k') goPrev();
  else if (key === 'p') decide('approve');
  else if (key === 'r') openReject();
  else if (e.key === '?') openHelp();
  else return;
  e.preventDefault();
}

onMounted(() => {
  loadQueues();
  window.addEventListener('keydown', onKeydown);
});
onUnmounted(() => window.removeEventListener('keydown', onKeydown));
</script>

<template>
  <div class="zy-business-page review-center flex flex-col gap-4">
    <BusinessPageHeader
      title="审核工作台"
      description="评论、作品与非遗项目的待审队列。逐条决策，处理后队列即时前进。"
      eyebrow="REVIEW PIPELINE"
      :loading="loading"
      @refresh="loadQueues"
    >
      <div class="header-actions">
        <el-button ref="helpBtnRef" @click="openHelp">快捷键（?）</el-button>
        <el-button :loading="loading" @click="loadQueues">刷新队列</el-button>
      </div>
    </BusinessPageHeader>

    <!-- 首次进入引导条（可关闭，localStorage 记忆） -->
    <div v-if="guideVisible" class="shortcut-bar zy-glass-chip" role="note" aria-label="键盘快捷键引导">
      <span><kbd>J</kbd> 下一条</span>
      <span><kbd>P</kbd> 通过</span>
      <span><kbd>R</kbd> 驳回（需填理由）</span>
      <span class="shortcut-note">输入框聚焦时快捷键不生效 · 按 <kbd>?</kbd> 查看全部快捷键</span>
      <button type="button" class="guide-close" aria-label="关闭快捷键引导" @click="dismissGuide">×</button>
    </div>

    <!-- 会话统计条（本地口径：只统计本次会话，不写后端） -->
    <p v-if="processedCount > 0" class="session-stats" aria-live="polite">
      本次会话已处理 {{ processedCount }} 条<template v-if="avgSeconds !== null"> · 平均 {{ avgSeconds }} 秒/条</template>
      <span class="session-stats-note">（会话口径，刷新清零；历史统计待后端支持）</span>
    </p>

    <!-- 错误态 -->
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    >
      <template #default>
        <el-button size="small" @click="loadQueues">重试</el-button>
      </template>
    </el-alert>

    <!-- 加载骨架 -->
    <el-skeleton v-if="loading && !queue.length" :rows="6" animated />

    <!-- 空队列完成态 -->
    <el-card v-else-if="!current" shadow="never" class="done-card">
      <div class="done-inner">
        <strong>队列已清空</strong>
        <p>
          本次会话已处理 {{ processedCount }} 条<template v-if="avgSeconds !== null">，平均 {{ avgSeconds }} 秒/条</template>。原页面（评论审核 / 作品管理 / 项目审核）刷新后状态一致。
        </p>
        <div class="done-actions">
          <el-button type="primary" @click="loadQueues">重新检查队列</el-button>
          <el-button @click="$router.push('/zhiyu/comments')">去评论审核页</el-button>
          <el-button @click="$router.push('/zhiyu/business/works')">去作品管理页</el-button>
          <el-button @click="$router.push('/zhiyu/business/audit')">去项目审核页</el-button>
        </div>
      </div>
    </el-card>

    <!-- 决策区 -->
    <div v-else-if="current" class="review-layout">
      <el-card shadow="never" class="decision-card">
        <div class="item-head">
          <span class="kind-chip" :class="current.kind">{{ KIND_LABELS[current.kind] }}</span>
          <span class="queue-pos">{{ currentIndex + 1 }} / {{ queue.length }}（剩余 {{ remaining }} 条）</span>
        </div>

        <template v-if="current.kind === 'comment'">
          <p class="content-main">{{ current.content }}</p>
          <p class="item-meta">
            {{ current.author }} · 评论于「{{ current.title }}」 · {{ formatTime(current.createTime) }}
          </p>
          <!-- BM：AI 辅助建议（按需调用，点击才触发真实模型调用） -->
          <AiSuggestPanel target-type="comment" :target-id="current.id" />
        </template>
        <template v-else-if="current.kind === 'project'">
          <p class="work-title">{{ current.title }}</p>
          <p class="item-meta">
            分类：{{ current.category }} · 级别：{{ current.level ?? '—' }} 级 · 预计 {{ current.estMinutes ?? '—' }} 分钟 · {{ formatTime(current.createTime) }}
          </p>
          <p class="item-meta">项目详情（步骤 / 材料 / 教学视频）请在「项目审核」页复核；本卡片只展示目录接口已返回字段。</p>
        </template>
        <template v-else-if="current.kind === 'campus'">
          <p class="work-title">{{ current.title }}</p>
          <p class="item-meta">
            提交时间：{{ formatTime(current.createTime) }} · 通过/驳回都需填写审核说明
          </p>
          <p class="item-meta">姓名与学号等身份明细不在队列展示，请在「校园身份审核」页核对后再回来决策。</p>
        </template>
        <template v-else>
          <div class="work-row">
            <el-image
              v-if="current.coverUrl"
              :src="current.coverUrl"
              :preview-src-list="[current.coverUrl]"
              fit="cover"
              class="work-cover"
              preview-teleported
            />
            <div>
              <p class="work-title">{{ current.title }}</p>
              <p class="item-meta">
                {{ current.author }} · 可见性：{{ dictLabel(WORK_VISIBLE, current.visible) }} · {{ formatTime(current.createTime) }}
              </p>
            </div>
          </div>
          <!-- BM：AI 辅助建议（媒体内容 AI 不可见，后端如实返回人工核对提示） -->
          <AiSuggestPanel target-type="work" :target-id="current.id" />
        </template>

        <div class="decision-actions">
          <el-button
            type="success"
            size="large"
            :loading="busy"
            @click="decide('approve')"
          >
            通过（P）
          </el-button>
          <el-button
            type="danger"
            plain
            size="large"
            :disabled="busy"
            @click="openReject"
          >
            驳回（R）
          </el-button>
        </div>
        <p class="decision-note">
          通过后内容公开；驳回需填理由（落库并通知作者），驳回后仅作者可见状态。评论处理带乐观校验（预期状态待审），冲突时本条保留可重试。
        </p>
      </el-card>

      <!-- 待处理预览 -->
      <el-card shadow="never" class="upcoming-card">
        <template #header>
          <span class="upcoming-title">接下来</span>
        </template>
        <p v-if="!upcoming.length" class="upcoming-empty">队列中只剩当前这一条</p>
        <button
          v-for="item in upcoming"
          :key="item.kind + item.id"
          type="button"
          class="upcoming-item"
          @click="currentIndex = queue.indexOf(item)"
        >
          <span class="kind-chip" :class="item.kind">{{ KIND_LABELS[item.kind] }}</span>
          <span class="upcoming-text">{{ item.kind === 'comment' ? item.content : item.title }}</span>
        </button>
      </el-card>
    </div>

    <p class="business-note">审核不改变账号权限；本系统不能替代专业心理咨询。</p>

    <!-- 理由弹窗（驳回必填；校园核验通过/驳回都必填说明） -->
    <el-dialog
      v-model="rejectVisible"
      :title="reasonDialogTitle"
      width="min(480px, 94vw)"
      :close-on-click-modal="false"
    >
      <p class="reject-hint">{{ reasonDialogHint }}</p>
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        :placeholder="pendingAction === 'approve' ? '如：已与学院教务名册核对一致' : '如：包含联系方式 / 与非遗主题无关'"
        @keyup.ctrl.enter="confirmReject"
      />
      <p v-if="rejectError" class="reject-error">{{ rejectError }}</p>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button
          :type="pendingAction === 'approve' ? 'success' : 'danger'"
          :loading="busy"
          @click="confirmReject"
        >
          {{ pendingAction === 'approve' ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 快捷键帮助面板（? 唤起；Esc 关闭；关闭后焦点归还「快捷键」按钮） -->
    <el-dialog
      v-model="helpVisible"
      title="键盘快捷键"
      width="min(440px, 94vw)"
      @closed="onHelpClosed"
    >
      <ul class="help-list">
        <li><kbd>J</kbd> 下一条</li>
        <li><kbd>K</kbd> 上一条</li>
        <li><kbd>P</kbd> 通过当前条目</li>
        <li><kbd>R</kbd> 驳回当前条目（需填理由）</li>
        <li><kbd>?</kbd> 打开本面板</li>
        <li><kbd>Esc</kbd> 关闭弹窗</li>
      </ul>
      <p class="help-note">输入框聚焦时快捷键不生效；接口无批量端点，本页逐条处理。校园核验的通过/驳回都会先弹出审核说明（服务端强制必填）。</p>
    </el-dialog>
  </div>
</template>

<style scoped>
.header-actions {
  display: flex;
  gap: 10px;
}

.session-stats {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.session-stats-note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.guide-close {
  padding: 2px 8px;
  font-size: 14px;
  line-height: 1;
  color: var(--el-text-color-regular);
  background: transparent;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
}

.guide-close:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.help-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 20px;
  padding: 0;
  margin: 0 0 12px;
  font-size: 13px;
  list-style: none;
}

.help-list kbd {
  display: inline-block;
  min-width: 20px;
  padding: 1px 6px;
  margin-right: 6px;
  font-weight: 600;
  text-align: center;
  border: 1px solid var(--el-border-color);
  border-bottom-width: 2px;
  border-radius: 6px;
}

.help-note {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.shortcut-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.shortcut-bar kbd {
  display: inline-block;
  min-width: 20px;
  padding: 1px 6px;
  margin-right: 4px;
  font-family: inherit;
  font-weight: 600;
  text-align: center;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-bottom-width: 2px;
  border-radius: 6px;
}

.shortcut-note {
  margin-left: auto;
  color: var(--el-text-color-secondary);
}

.review-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.9fr) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.kind-chip {
  display: inline-block;
  padding: 3px 10px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 999px;
}

.kind-chip.comment {
  color: var(--el-color-primary-dark-2);
  background: var(--el-color-primary-light-9);
}

.kind-chip.work {
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
}

.kind-chip.project {
  color: var(--el-color-success-dark-2);
  background: var(--el-color-success-light-9);
}

.kind-chip.campus {
  color: var(--el-color-info-dark-2);
  background: var(--el-color-info-light-9);
}

.queue-pos {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.content-main {
  margin: 0 0 14px;
  font-size: 17px;
  line-height: 1.9;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: var(--el-text-color-primary);
}

.work-row {
  display: flex;
  gap: 18px;
  align-items: flex-start;
}

.work-cover {
  width: 120px;
  height: 120px;
  flex-shrink: 0;
  border-radius: 10px;
}

.work-title {
  margin: 0 0 10px;
  font-size: 17px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.item-meta {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.decision-actions {
  display: flex;
  gap: 14px;
  margin-top: 26px;
}

.decision-note {
  margin: 16px 0 0;
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}

.upcoming-title {
  font-size: 14px;
  font-weight: 600;
}

.upcoming-empty {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.upcoming-item {
  display: flex;
  width: 100%;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 0;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
}

.upcoming-item:last-child {
  border-bottom: 0;
}

.upcoming-text {
  display: -webkit-box;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.upcoming-item:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.done-card .done-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 40px 0;
  text-align: center;
}

.done-inner strong {
  font-size: 18px;
}

.done-inner p {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.done-actions {
  display: flex;
  gap: 12px;
  margin-top: 10px;
}

.reject-hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.reject-error {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-color-danger);
}

@media (max-width: 900px) {
  .review-layout {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .review-center * {
    transition: none !important;
  }
}
</style>
