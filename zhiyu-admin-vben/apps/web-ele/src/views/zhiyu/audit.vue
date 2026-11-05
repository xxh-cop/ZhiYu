<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage } from 'element-plus';

import {
  auditProject,
  deleteProject,
  getProjectDetail,
  takedownProject,
} from '#/api/zhiyu/admin';
import { requestClient } from '#/api/request';
import ZyListPage from '#/components/zy-list-page/ZyListPage.vue';
import { useListPage } from '#/components/zy-list-page/useListPage';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import {
  dictLabel,
  PROJECT_ACTION_ORIGIN,
  PROJECT_LEVEL,
  PROJECT_STATUS,
} from '#/constants/status-dict';

import VideoPreview from './components/VideoPreview.vue';

const route = useRoute();

/** 类型筛选选项 */
const typeOptions = [{ value: 'project', label: '项目' }];
const filterType = ref('project');
const catalogStatus = ref(
  ['0', '1', '2', '3'].includes(String(route.query.status))
    ? Number(route.query.status)
    : 0,
);
const catalogCategory = ref(String(route.query.category || ''));
const searchText = ref('');

/** 空态文案（BN3）：筛归档时说明「暂无归档项目」，不显示成加载失败/通用无匹配 */
const catalogEmptyText = computed(() =>
  Number(catalogStatus.value) === 3 ? '暂无归档项目' : '没有匹配的项目',
);

/**
 * 列表状态机。
 * 后端 /admin/project-catalog 每页固定 10 条（LIMIT 10），
 * 故 show-sizes 关闭、fetcher 不透传 pageSize。
 * 标题搜索由服务端 title LIKE 完成，不再保留前端二次过滤。
 */
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
} = useListPage({
  pageSize: 10,
  fetcher: ({ page }) =>
    requestClient.get('/admin/project-catalog', {
      params: {
        page,
        status: catalogStatus.value,
        category: catalogCategory.value,
        keyword: searchText.value,
      },
    }),
});

/** 驳回弹窗 */
const rejectDialogVisible = ref(false);
const rejectRow = ref<any>(null);
const rejectReason = ref('');
const rejectLoading = ref(false);

/** 下架弹窗（BA 2026-11-05，后端 AY1；分层下架契约：kind 必填，operational 需 archiveAt） */
const takedownDialogVisible = ref(false);
const takedownRow = ref<any>(null);
const takedownReason = ref('');
/** 下架类型：compliance 合规 / operational 运营（必填，不预选，防手滑默认） */
const takedownKind = ref('' as '' | 'compliance' | 'operational');
/** 运营下架的归档截止时间（ISO 本地格式；默认预填 14 天，最小 7 天前端校验） */
const takedownArchiveAt = ref('');
const takedownLoading = ref(false);

/**
 * 删除弹窗（BA，后端 AY2：逻辑删除 + 级联步骤/材料 + 退役孤儿媒体）。
 * 危险操作分层：删除不可逆且级联清理，二次确认采用「输入项目标题」——
 * 强制操作者停顿核对对象，避免勾选确认被肌肉记忆直接点过；
 * 下架为常规操作（可通知、终态但不删除数据），只用理由弹窗。
 */
const deleteDialogVisible = ref(false);
const deleteRow = ref<any>(null);
const deleteReason = ref('');
const deleteConfirmText = ref('');
const deleteLoading = ref(false);
const deleteConfirmOk = computed(
  () => deleteConfirmText.value.trim() === deleteRow.value?.title,
);

/** 教学视频预览 */
const videoToReview = ref('');

/**
 * 问责详情（BF 2026-11-05，后端 BC1）。
 * status=2 的两种来源（评审驳回 REJECT / 事后下架 TAKEDOWN）用不同标签色区分；
 * lastAction 为 null（2026-11-05 前的历史数据无操作日志）时如实说明，不留空白。
 */
const detailVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref('');
const detail = ref<any>(null);
let detailTurn = 0;

async function openDetail(row: any) {
  const turn = ++detailTurn;
  detailVisible.value = true;
  detailLoading.value = true;
  detailError.value = '';
  detail.value = null;
  try {
    const data = await getProjectDetail(String(row.id));
    if (turn !== detailTurn) return;
    detail.value = data;
  } catch (e: any) {
    if (turn !== detailTurn) return;
    detailError.value = e?.response?.data?.message || '详情加载失败，请稍后重试。';
  } finally {
    if (turn === detailTurn) detailLoading.value = false;
  }
}

function closeDetail() {
  detailTurn++;
  detailVisible.value = false;
  detail.value = null;
  detailError.value = '';
}

/** 详情内执行下架/删除：复用 BA 的同一套弹窗（不造第二套），先关详情避免双弹窗叠层 */
function actFromDetail(kind: 'delete' | 'takedown') {
  const row = detail.value;
  closeDetail();
  if (kind === 'takedown') openTakedown(row);
  else openDelete(row);
}

/** 格式化时间 */
function formatTime(dt?: string) {
  if (!dt) return '-';
  return dt.replace('T', ' ').substring(0, 16);
}

/** 通过 */
async function handleApprove(row: any) {
  try {
    await auditProject(row.id, { action: 'approve' });
    ElMessage.success(`「${row.title}」已通过`);
    await fetchList();
  } catch {
    // 拦截器已弹错误提示
  }
}

/** 打开驳回弹窗 */
function handleReject(row: any) {
  rejectRow.value = row;
  rejectReason.value = '';
  rejectDialogVisible.value = true;
}

/** 确认驳回 */
async function confirmReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回理由');
    return;
  }
  rejectLoading.value = true;
  try {
    await auditProject(rejectRow.value.id, {
      action: 'reject',
      reason: rejectReason.value.trim(),
    });
    ElMessage.success(`「${rejectRow.value.title}」已驳回`);
    rejectDialogVisible.value = false;
    await fetchList();
  } catch {
    // 拦截器已弹错误提示（弹窗保留可重试）
  } finally {
    rejectLoading.value = false;
  }
}

/** 打开下架弹窗（仅上架态入口；运营下架默认预填 14 天，对齐后端 default-deadline-days） */
function openTakedown(row: any) {
  takedownRow.value = row;
  takedownReason.value = '';
  takedownKind.value = '';
  const d = new Date(Date.now() + 14 * 24 * 3600 * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  takedownArchiveAt.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:00`;
  takedownDialogVisible.value = true;
}

/** 确认下架 */
async function confirmTakedown() {
  const reason = takedownReason.value.trim();
  if (!takedownKind.value) {
    ElMessage.warning('请选择下架类型（合规 / 运营）');
    return;
  }
  if (!reason) {
    ElMessage.warning('请填写下架理由');
    return;
  }
  if (takedownKind.value === 'operational') {
    if (!takedownArchiveAt.value) {
      ElMessage.warning('运营下架请选择归档截止时间');
      return;
    }
    const at = new Date(takedownArchiveAt.value).getTime();
    if (Number.isNaN(at) || at < Date.now() + 7 * 24 * 3600 * 1000) {
      ElMessage.warning('归档截止时间距现在至少 7 天');
      return;
    }
  }
  takedownLoading.value = true;
  try {
    await takedownProject(takedownRow.value.id, {
      reason,
      kind: takedownKind.value as 'compliance' | 'operational',
      ...(takedownKind.value === 'operational'
        ? { archiveAt: takedownArchiveAt.value }
        : {}),
    });
    ElMessage.success(`「${takedownRow.value.title}」已下架`);
    takedownDialogVisible.value = false;
    await fetchList();
  } catch {
    // 3001/1001 等后端原文由拦截器上屏（弹窗保留可重试）
  } finally {
    takedownLoading.value = false;
  }
}

/** 打开删除弹窗（上架/下架态入口；二次确认=输入项目标题） */
function openDelete(row: any) {
  deleteRow.value = row;
  deleteReason.value = '';
  deleteConfirmText.value = '';
  deleteDialogVisible.value = true;
}

/** 确认删除 */
async function confirmDelete() {
  const reason = deleteReason.value.trim();
  if (!reason) {
    ElMessage.warning('请填写删除理由');
    return;
  }
  if (!deleteConfirmOk.value) {
    ElMessage.warning('请完整输入项目标题以确认删除');
    return;
  }
  deleteLoading.value = true;
  try {
    await deleteProject(deleteRow.value.id, reason);
    ElMessage.success(`「${deleteRow.value.title}」已删除`);
    deleteDialogVisible.value = false;
    await fetchList();
  } catch {
    // 后端原文由拦截器上屏（弹窗保留可重试）
  } finally {
    deleteLoading.value = false;
  }
}
</script>

<template>
  <ZyListPage
    v-model:page="currentPage"
    v-model:page-size="pageSize"
    title="项目审核"
    description="按分类和上架状态查看项目，处理待审核内容；上架项目可下架、删除。状态「下架」为审核驳回与管理员下架的共同终态（后端同一口径，理由经通知送达传承人）。"
    :data="tableData"
    :loading="loading"
    :error="errorMsg"
    :total="total"
    :empty-text="catalogEmptyText"
    empty-hint="传承人提交项目后会进入待审核；可调整分类与上架状态筛选"
    :show-sizes="false"
    @refresh="fetchList"
    @page-change="handlePageChange"
  >
    <template #filters>
      <el-select
        v-model="catalogStatus"
        style="width: 140px"
        aria-label="项目状态"
        @change="handleFilterChange"
      >
        <el-option :value="0" label="待审" />
        <el-option :value="1" label="上架" />
        <el-option :value="2" label="下架" />
        <el-option :value="3" label="归档" />
      </el-select>
      <el-input
        v-model="catalogCategory"
        placeholder="分类（可选）"
        clearable
        style="width: 160px"
        @change="handleFilterChange"
      />
      <el-select
        v-model="filterType"
        placeholder="类型"
        style="width: 140px"
        @change="handleFilterChange"
      >
        <el-option
          v-for="opt in typeOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-input
        v-model="searchText"
        placeholder="搜索标题"
        clearable
        style="width: 260px"
        @change="handleFilterChange"
        @clear="handleFilterChange"
      />
    </template>

    <el-table-column prop="id" label="ID" width="80" />
    <el-table-column prop="title" label="标题" min-width="220" />
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <ZyStatusTag :dict="PROJECT_STATUS" :value="row.status" />
      </template>
    </el-table-column>
    <el-table-column label="教学视频" width="110">
      <template #default="{ row }">
        <el-button
          v-if="row.videoUrl"
          link
          type="primary"
          @click="videoToReview = row.videoUrl"
        >
          预览视频
        </el-button>
        <span v-else>无视频</span>
      </template>
    </el-table-column>
    <el-table-column prop="category" label="品类" width="100" />
    <el-table-column label="难度" width="80">
      <template #default="{ row }">
        {{ dictLabel(PROJECT_LEVEL, row.level) }}
      </template>
    </el-table-column>
    <el-table-column prop="estMinutes" label="时长(分)" width="100" />
    <el-table-column label="提交时间" width="160">
      <template #default="{ row }">
        {{ formatTime(row.createTime) }}
      </template>
    </el-table-column>
    <!-- BA：操作按状态分层——待审给审核动作；上架给下架/删除；下架/驳回终态只保留删除，不摆点了报 3001 的按钮。
         BF：各态均有「详情」问责入口。 -->
    <el-table-column label="操作" width="220" fixed="right">
      <template #default="{ row }">
        <el-button type="primary" link size="small" @click="openDetail(row)">
          详情
        </el-button>
        <template v-if="Number(row.status) === 0">
          <el-button type="primary" size="small" @click="handleApprove(row)">
            通过
          </el-button>
          <el-button type="danger" size="small" @click="handleReject(row)">
            驳回
          </el-button>
        </template>
        <template v-else-if="Number(row.status) === 1">
          <el-button type="warning" size="small" @click="openTakedown(row)">
            下架
          </el-button>
          <el-button type="danger" plain size="small" @click="openDelete(row)">
            删除
          </el-button>
        </template>
        <template v-else>
          <el-button type="danger" plain size="small" @click="openDelete(row)">
            删除
          </el-button>
        </template>
      </template>
    </el-table-column>

    <template #append>
      <!-- 问责详情弹窗（BF：动作来源 + 理由全文，操作复用 BA 弹窗） -->
      <el-dialog
        v-model="detailVisible"
        title="项目问责详情"
        width="min(520px, 92vw)"
        @closed="closeDetail"
      >
        <el-skeleton v-if="detailLoading" :rows="4" animated />
        <el-alert
          v-else-if="detailError"
          :title="detailError"
          type="error"
          :closable="false"
          show-icon
        />
        <template v-else-if="detail">
          <dl class="detail-grid">
            <div><dt>项目</dt><dd>{{ detail.title }}</dd></div>
            <div><dt>当前状态</dt><dd><ZyStatusTag :dict="PROJECT_STATUS" :value="detail.status" /></dd></div>
            <div>
              <dt>最近一次动作</dt>
              <dd>
                <template v-if="detail.lastAction">
                  <ZyStatusTag :dict="PROJECT_ACTION_ORIGIN" :value="detail.lastAction" />
                </template>
                <span v-else class="note-text">无</span>
              </dd>
            </div>
            <div><dt>动作时间</dt><dd>{{ detail.lastActionTime ? formatTime(detail.lastActionTime) : '-' }}</dd></div>
            <div><dt>操作者</dt><dd class="mono">{{ detail.lastActorId || '-' }}</dd></div>
            <div><dt>提交时间</dt><dd>{{ formatTime(detail.createTime) }}</dd></div>
          </dl>
          <template v-if="Number(detail.status) === 2">
            <h4 class="block-title">下架/驳回来源</h4>
            <p class="note-text">
              <template v-if="detail.statusOrigin === 'TAKEDOWN'">
                <ZyStatusTag :dict="PROJECT_ACTION_ORIGIN" value="TAKEDOWN" />
                该项目是上架后被管理员事后下架的（非评审驳回）。
              </template>
              <template v-else-if="detail.statusOrigin === 'REJECT'">
                <ZyStatusTag :dict="PROJECT_ACTION_ORIGIN" value="REJECT" />
                该项目是送审后被评审驳回的（非事后下架）。
              </template>
              <template v-else>
                历史数据无操作日志可推导来源，仅知当前状态为「下架」（2026-11-05 前的下架态均为评审驳回）。
              </template>
            </p>
            <!-- BN3：下架三键（takedownKind/exportUntil/archiveAt）恒下发、无值为 null；
                 仅下架态且有类型时渲染该块；null 不渲染（不留空行/空白标签） -->
            <p v-if="detail.takedownKind" class="note-text">
              <template v-if="detail.takedownKind === 'compliance'">
                合规下架，可导出至 {{ formatTime(detail.exportUntil) }}（到期归档）。
              </template>
              <template v-else-if="detail.takedownKind === 'operational'">
                运营下架，{{ formatTime(detail.archiveAt) }} 到期归档。
              </template>
            </p>
          </template>
          <p v-if="!detail.lastAction" class="note-text">
            该项目暂无操作日志（2026-11-05 起审核/下架/删除动作才落日志），仅知当前状态。
          </p>
          <h4 class="block-title">最近一次动作理由</h4>
          <p class="reason-text">{{ detail.auditReason || '（无理由记录）' }}</p>
          <div v-if="Number(detail.status) !== 0" class="detail-actions">
            <el-button
              v-if="Number(detail.status) === 1"
              type="warning"
              @click="actFromDetail('takedown')"
            >
              下架该项目
            </el-button>
            <el-button type="danger" plain @click="actFromDetail('delete')">
              删除该项目
            </el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 驳回弹窗 -->
      <el-dialog
        v-model="rejectDialogVisible"
        title="驳回项目"
        width="440px"
      >
        <p class="mb-3 business-note">请填写驳回理由（将通知传承人）</p>
        <el-input
          v-model="rejectReason"
          type="textarea"
          :rows="3"
          placeholder="请输入驳回理由"
          maxlength="200"
          show-word-limit
        />
        <template #footer>
          <el-button @click="rejectDialogVisible = false">取消</el-button>
          <el-button type="danger" :loading="rejectLoading" @click="confirmReject">
            确认驳回
          </el-button>
        </template>
      </el-dialog>

      <!-- 下架弹窗（常规操作：终态但保留数据，通知传承人；分层下架：kind 必选） -->
      <el-dialog
        v-model="takedownDialogVisible"
        title="下架项目"
        width="min(520px, 92vw)"
      >
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="下架后学生端与传承人端将立即无法访问该项目（含步骤音频），且下架为终态、不可再审核复活。"
          class="mb-3"
        />
        <p class="mb-2 business-note">下架类型（必选）：</p>
        <el-radio-group v-model="takedownKind" class="mb-3 takedown-kind">
          <el-radio value="compliance" border>合规下架</el-radio>
          <el-radio value="operational" border>运营下架</el-radio>
        </el-radio-group>
        <el-alert
          v-if="takedownKind === 'compliance'"
          type="info"
          :closable="false"
          show-icon
          title="合规下架：公开面即收，进行中/暂停的创作会话转只读（可回看不可推进）；将给予 72 小时导出窗口，到期自动归档。"
          class="mb-3"
        />
        <template v-if="takedownKind === 'operational'">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="运营下架：不再接受新创作；存量创作会话可继续到下方截止时间，到期未完成会话转只读并归档。"
            class="mb-3"
          />
          <p class="mb-2 business-note">
            归档截止时间（距现在至少 7 天，默认预填 14 天）：
          </p>
          <el-date-picker
            v-model="takedownArchiveAt"
            type="datetime"
            placeholder="选择归档截止时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            :disabled-date="(d: Date) => d.getTime() < Date.now() + 6 * 24 * 3600 * 1000"
            class="mb-3"
            style="width: 100%"
          />
        </template>
        <p class="mb-3 business-note">
          请填写下架理由（将通知传承人，≤300 字）。
        </p>
        <el-input
          v-model="takedownReason"
          type="textarea"
          :rows="3"
          placeholder="请输入下架理由"
          maxlength="300"
          show-word-limit
        />
        <template #footer>
          <el-button @click="takedownDialogVisible = false">取消</el-button>
          <el-button
            type="warning"
            :loading="takedownLoading"
            :disabled="!takedownReason.trim()"
            @click="confirmTakedown"
          >
            确认下架
          </el-button>
        </template>
      </el-dialog>

      <!-- 删除弹窗（危险操作：二次确认=输入项目标题） -->
      <el-dialog
        v-model="deleteDialogVisible"
        title="删除项目"
        width="min(480px, 92vw)"
      >
        <el-alert
          type="error"
          :closable="false"
          show-icon
          title="删除为不可逆操作：项目及其步骤、材料将被逻辑删除，不再被引用的音频/视频/封面会被自动退役清理。"
          class="mb-3"
        />
        <p class="mb-3 business-note">
          请填写删除理由（将通知传承人，≤300 字）。
        </p>
        <el-input
          v-model="deleteReason"
          type="textarea"
          :rows="3"
          placeholder="请输入删除理由"
          maxlength="300"
          show-word-limit
        />
        <p class="mb-2 mt-4 business-note">
          请输入项目标题「{{ deleteRow?.title }}」以确认删除：
        </p>
        <el-input
          v-model="deleteConfirmText"
          :placeholder="deleteRow?.title"
          clearable
        />
        <template #footer>
          <el-button @click="deleteDialogVisible = false">取消</el-button>
          <el-button
            type="danger"
            :loading="deleteLoading"
            :disabled="!deleteReason.trim() || !deleteConfirmOk"
            @click="confirmDelete"
          >
            确认删除
          </el-button>
        </template>
      </el-dialog>

      <!-- 教学视频预览弹窗 -->
      <el-dialog
        :model-value="!!videoToReview"
        title="教学视频审核"
        width="min(720px, 94vw)"
        @close="videoToReview = ''"
      >
        <VideoPreview v-if="videoToReview" :src="videoToReview" />
        <p>确认教学内容、画面和音轨授权后，再通过项目审核。</p>
      </el-dialog>
    </template>
  </ZyListPage>
</template>

<style scoped>
/* BF：问责详情 */
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  margin: 0 0 12px;
}
.detail-grid dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.detail-grid dd {
  margin: 2px 0 0;
}
.block-title {
  margin: 12px 0 6px;
  font-size: 14px;
  font-weight: 600;
}
.note-text {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}
.reason-text {
  margin: 0;
  max-height: 160px;
  overflow-y: auto;
  padding: 10px 12px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}
.mono {
  font-family: var(--el-font-family-monospace, monospace);
  font-size: 12px;
}
.detail-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
