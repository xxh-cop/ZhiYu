<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@vben/stores';

import { requestClient } from '#/api/request';
import ZyListPage from '#/components/zy-list-page/ZyListPage.vue';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import {
  BOOKING_STATUS,
  dictLabel,
  PROGRAM_KIND,
  PROGRAM_STATUS,
} from '#/constants/status-dict';
import { confirmAction } from '#/utils/confirm-action';

import CourseLessons from './components/CourseLessons.vue';
import GuidanceMessages from './components/GuidanceMessages.vue';
import ProgramFeedback from './components/ProgramFeedback.vue';
import ProgramTeam from './components/ProgramTeam.vue';
import { allowedStatuses } from './programs-status';

type Program = { id: string; kind: string; title: string; description: string; location: string; co_leader: string; materials: string; start_time: string; end_time: string; capacity: number; status: string; booked: number };
type WorkshopTemplate = { id: string; title: string; summary: string; kind: string; minutes: number; capacity: number; description: string; materials: string; safety: string; sourceNote: string; tags: string[] };
type WorkshopTemplateCatalog = { version: string; disclaimer: string; templates: WorkshopTemplate[] };
type Attendee = { id: string; nickname: string; status: string };

const route = useRoute();

/** 列表数据（服务端一次返回全部，前端筛选 + 本地分页） */
const rows = ref<Program[]>([]);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const query = ref('');

/** 弹窗显隐 */
const dialog = ref(false);
const selected = ref('');
const teamDialog = ref(false);
const teamId = ref('');
const messageDialog = ref(false);
const feedbackDialog = ref(false);
const feedbackId = ref('');
const courseDialog = ref(false);
const courseId = ref('');

/** 报名名单弹窗 */
const attendeeDialog = ref(false);
const attendees = ref<Attendee[]>([]);
const attendeeProgram = ref('');
const checking = ref('');
const attendeeLoading = ref(false);
const attendeeError = ref('');
const attendeeTitle = ref('');
const pendingCount = computed(() => attendees.value.filter((a) => a.status === 'PENDING').length);

/** 筛选 */
const statusFilter = ref('ALL');
const kindFilter = ref(
  ['WORKSHOP', 'COURSE', 'GUIDANCE'].includes(String(route.query.kind))
    ? String(route.query.kind)
    : 'ALL',
);
const statusCards = computed(() => [
  { id: 'ALL', label: '当前列表', count: rows.value.length },
  ...['DRAFT', 'PUBLISHED', 'CANCELLED'].map((id) => ({
    id,
    label: id === 'DRAFT' ? '待完善草稿' : id === 'PUBLISHED' ? '已发布' : '已取消',
    count: rows.value.filter((p) => p.status === id).length,
  })),
]);

/** 文案统一取状态字典（PROGRAM_KIND / PROGRAM_STATUS / BOOKING_STATUS） */
function kindLabel(kind: string) {
  return dictLabel(PROGRAM_KIND, kind);
}
function programStatusLabel(status: string) {
  return dictLabel(PROGRAM_STATUS, status);
}

/** 新建 / 编辑表单 */
const form = reactive({ kind: 'WORKSHOP', title: '', description: '', location: '', coLeader: '', materials: '', startTime: '', endTime: '', capacity: 12, status: 'DRAFT' });

/** 工作坊模板 */
const workshopTemplates = ref<WorkshopTemplate[]>([]);
const templateId = ref('');
const templateError = ref('');
const templateApplied = ref(false);
const templateDuration = ref(0);
const selectedTemplate = computed(
  () => workshopTemplates.value.find((t) => t.id === templateId.value) || null,
);

/** 筛选后的完整列表 */
const visible = computed(() =>
  rows.value.filter(
    (p) =>
      (kindFilter.value === 'ALL' || p.kind === kindFilter.value) &&
      (statusFilter.value === 'ALL' || p.status === statusFilter.value) &&
      `${p.title} ${p.location}`.includes(query.value.trim()),
  ),
);

/** 本地分页 */
const page = ref(1);
const pageSize = 10;
const pagedRows = computed(() =>
  visible.value.slice((page.value - 1) * pageSize, page.value * pageSize),
);
const editingStatus = ref('DRAFT');

watch([kindFilter, statusFilter, query], () => {
  page.value = 1;
});
watch(
  () => visible.value.length,
  (len) => {
    const maxPage = Math.max(1, Math.ceil(len / pageSize));
    if (page.value > maxPage) page.value = maxPage;
  },
);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    rows.value = await requestClient.get('/growth/programs/manage');
  } catch {
    error.value = '加载失败，请重试。';
  } finally {
    loading.value = false;
  }
}

async function loadTemplates() {
  try {
    const result = await requestClient.get<WorkshopTemplateCatalog>('/growth/workshop-templates');
    workshopTemplates.value = result.templates || [];
    templateError.value = '';
  } catch {
    workshopTemplates.value = [];
    templateError.value = '模板暂时不可用，可以手动填写活动信息。';
  }
}

function plusMinutes(value: string, minutes: number) {
  const date = new Date(value);
  if (!value || Number.isNaN(date.getTime())) return value;
  date.setMinutes(date.getMinutes() + minutes);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`;
}

async function applyTemplate() {
  const t = selectedTemplate.value;
  if (!t) return;
  if (
    (form.title.trim() || form.description.trim()) &&
    !(await confirmAction({
      confirmText: '应用模板',
      message: '将用模板内容覆盖当前标题、说明、材料和名额；开始时间、结束时间与地点会保留。',
      title: '应用工作坊模板',
    }))
  )
    return;
  form.kind = 'WORKSHOP';
  form.title = t.title;
  form.description = `${t.description}\n\n来源与边界：${t.sourceNote}`;
  form.materials = t.materials;
  form.capacity = t.capacity;
  form.status = 'DRAFT';
  templateDuration.value = t.minutes;
  templateApplied.value = true;
  if (form.startTime) form.endTime = plusMinutes(form.startTime, t.minutes);
  ElMessage.success('模板已填入，请补充开始时间与地点后保存');
}

watch(
  () => form.startTime,
  (value) => {
    if (templateApplied.value && value) form.endTime = plusMinutes(value, templateDuration.value);
  },
);
watch(templateId, () => {
  templateApplied.value = false;
});

function edit(p?: Program) {
  editingStatus.value = p?.status ?? 'DRAFT';
  selected.value = p?.id || '';
  templateId.value = '';
  templateApplied.value = false;
  templateDuration.value = 0;
  Object.assign(
    form,
    p
      ? { kind: p.kind, title: p.title, description: p.description, location: p.location, coLeader: p.co_leader, materials: p.materials, startTime: p.start_time, endTime: p.end_time, capacity: p.capacity, status: p.status }
      : { kind: 'WORKSHOP', title: '', description: '', location: '', coLeader: '', materials: '', startTime: '', endTime: '', capacity: 12, status: 'DRAFT' },
  );
  dialog.value = true;
}

async function template() {
  if (
    form.description &&
    !(await confirmAction({
      confirmText: '替换',
      message: '用八周课程结构替换当前课程说明？现有说明会被覆盖。',
      title: '替换课程说明',
    }))
  )
    return;
  form.kind = 'COURSE';
  form.description = '第 1 周：认识材料与安全约定\n第 2 周：感官觉察与基础技法\n第 3 周：以色彩表达当下\n第 4 周：体验节奏与重复\n第 5 周：纹样与个人故事\n第 6 周：合作创作与边界\n第 7 周：整理作品与回看\n第 8 周：分享与后续计划\n\n请补充每周实际日期、地点、带领者与所需材料。课程不替代专业心理咨询。';
}

async function save() {
  if (saving.value) return;
  if (!form.title.trim() || !form.description.trim() || !form.location.trim() || !form.startTime || !form.endTime) {
    ElMessage.warning('请填写标题、说明、地点和完整日程');
    return;
  }
  if (form.endTime <= form.startTime) {
    ElMessage.warning('结束时间须晚于开始时间');
    return;
  }
  if (form.status === 'CANCELLED' && editingStatus.value !== 'CANCELLED') {
    const confirmed = await confirmAction({
      confirmText: '取消活动',
      danger: true,
      message: '取消后用户会看到已取消状态，且无法恢复到发布状态。',
      title: '取消活动',
    });
    if (!confirmed) return;
  }
  saving.value = true;
  try {
    const body = { ...form, capacity: form.kind === 'GUIDANCE' ? 1 : form.capacity };
    if (selected.value) await requestClient.put(`/growth/programs/${selected.value}`, body);
    else await requestClient.post('/growth/programs', body);
    dialog.value = false;
    ElMessage.success('已保存');
    await load();
  } catch {
    /* shared request handler displays API error */
  } finally {
    saving.value = false;
  }
}

async function showAttendees(p: Program) {
  attendees.value = [];
  attendeeProgram.value = p.id;
  attendeeTitle.value = p.title;
  attendeeDialog.value = true;
  await reloadAttendees();
}

async function reloadAttendees() {
  attendeeLoading.value = true;
  attendeeError.value = '';
  try {
    attendees.value = await requestClient.get('/growth/programs/' + attendeeProgram.value + '/attendees');
  } catch {
    attendeeError.value = '报名名单加载失败，请重试';
  } finally {
    attendeeLoading.value = false;
  }
}

async function review(a: Attendee, decision: 'CONFIRM' | 'REJECT') {
  if (checking.value) return;
  try {
    await ElMessageBox.confirm(
      decision === 'CONFIRM' ? '确认接受这条一对一预约？' : '拒绝后将释放名额，用户会看到未通过状态。',
      decision === 'CONFIRM' ? '确认预约' : '拒绝预约',
      { type: decision === 'CONFIRM' ? 'info' : 'warning' },
    );
    checking.value = a.id;
    await requestClient.post('/growth/programs/' + attendeeProgram.value + '/bookings/' + a.id + '/review', { decision });
    ElMessage.success(decision === 'CONFIRM' ? '预约已确认' : '已拒绝并释放名额');
    await reloadAttendees();
    await load();
  } catch {
    /* request handler shows server errors */
  } finally {
    checking.value = '';
  }
}

async function checkin(a: Attendee) {
  if (checking.value) return;
  try {
    await ElMessageBox.confirm(`确认为 ${a.nickname} 签到？`, '签到确认');
    checking.value = a.id;
    await requestClient.post(`/growth/programs/${attendeeProgram.value}/checkin`, { bookingId: a.id });
    a.status = 'CHECKED_IN';
    ElMessage.success('签到成功');
  } catch {
    /* request handler shows server errors */
  } finally {
    checking.value = '';
  }
}

/**
 * 通知中心落点：?bookingId= 定位预约。
 * AW3（2026-11-05）：后端 AU2 已交付 GET /api/admin/bookings/{id}（仅 role=3，
 * 返回预约 + program 摘要）。管理员直接反查（1 次请求）；role 1/2 无该端点权限，
 * 回落到既有的有界扫描（本人可见活动名单，最多前 20 个）。
 * 找到则打开「预约处理中心」弹窗并高亮该行；找不到给明确说明，不假装定位成功。
 */
const highlightBookingId = ref('');
const bookingNotice = ref('');
const locating = ref(false);

/** 当前用户角色码（与 works.vue 同一口径；AW3：管理员反查通道判定） */
const userStore = useUserStore();
const isAdmin = computed(() => (userStore.userInfo?.roles ?? []).includes('ROLE_3'));

function attendeeRowClass({ row }: { row: Attendee }) {
  return String(row.id) === highlightBookingId.value ? 'zy-row-located' : '';
}

/** 定位成功后的统一动作：开名单弹窗 + 高亮 + 滚动到该行 */
async function revealBooking(p: Program, bookingId: string) {
  highlightBookingId.value = String(bookingId);
  await showAttendees(p);
  await nextTick();
  document
    .querySelector('.el-dialog .zy-row-located')
    ?.scrollIntoView({ block: 'center' });
  ElMessage.success(`来自通知：已定位到「${p.title}」中的该预约`);
}

async function locateBooking(bookingId: string) {
  if (locating.value) return;
  locating.value = true;
  try {
    // AW3：管理员优先走后端反查端点（不再逐活动扫描）
    if (isAdmin.value) {
      try {
        const detail = await requestClient.get('/admin/bookings/' + bookingId);
        const program = (detail as any)?.program;
        if (!program?.id) {
          bookingNotice.value = '该预约已没有关联活动（对应活动可能已移除）。';
          return;
        }
        const target = rows.value.find((p) => String(p.id) === String(program.id));
        if (!target) {
          bookingNotice.value = `该预约属于活动「${program.title}」，当前列表未加载到该活动（可能已取消或筛选排除）。`;
          return;
        }
        await revealBooking(target, bookingId);
        return;
      } catch (e: any) {
        // 1004 预约不存在（后端原文）；其余异常回落到有界扫描兜底
        const code = e?.response?.data?.code;
        if (code === 1004) {
          bookingNotice.value = '未找到该预约（可能已删除或撤回）。';
          return;
        }
      }
    }
    // role 1/2（无反查端点权限）或反查异常兜底：既有有界扫描
    for (const p of rows.value.slice(0, 20)) {
      let list: Attendee[] = [];
      try {
        list = await requestClient.get('/growth/programs/' + p.id + '/attendees');
      } catch {
        continue; // 单个活动名单失败不阻断定位
      }
      if (list.some((a) => String(a.id) === String(bookingId))) {
        await revealBooking(p, bookingId);
        return;
      }
    }
    bookingNotice.value = '未找到该预约（可能已删除、撤回或对应活动已移除）。';
  } finally {
    locating.value = false;
  }
}

onMounted(async () => {
  await load();
  loadTemplates();
  const bookingId = route.query.bookingId;
  if (typeof bookingId === 'string' && bookingId) {
    await locateBooking(bookingId);
  }
});
</script>

<template>
  <ZyListPage
    v-model:page="page"
    :page-size="pageSize"
    title="活动与预约管理"
    eyebrow="PROGRAM OPERATIONS"
    description="安排工作坊与课程，及时处理一对一申请，让每一次相聚都有清晰的下一步。"
    :data="pagedRows"
    :loading="loading"
    :error="error"
    :total="visible.length"
    empty-text="暂无活动，创建草稿后可发布"
    empty-hint="点击「新建活动」开始创建"
    :show-sizes="false"
    @refresh="load"
  >
    <template #headerActions>
      <div class="flex flex-wrap gap-2">
        <el-button @click="messageDialog = true">本人预约交流</el-button>
        <el-button type="primary" @click="edit()">新建活动</el-button>
      </div>
    </template>

    <template #banner>
      <div class="status-cards">
        <button
          v-for="card in statusCards"
          :key="card.id"
          :class="{ active: statusFilter === card.id }"
          :aria-pressed="statusFilter === card.id"
          @click="statusFilter = card.id"
        >
          <span>{{ card.label }}</span>
          <strong>{{ loading ? '—' : card.count }}</strong>
        </button>
      </div>
      <el-alert
        v-if="bookingNotice"
        :title="bookingNotice"
        type="warning"
        :closable="false"
        show-icon
        style="margin-top: 12px"
      />
    </template>

    <template #filters>
      <el-select v-model="kindFilter" style="width: 150px" aria-label="活动类型">
        <el-option value="ALL" label="全部类型" />
        <el-option v-for="k in ['WORKSHOP', 'COURSE', 'GUIDANCE']" :key="k" :value="k" :label="kindLabel(k)" />
      </el-select>
      <el-input v-model="query" placeholder="搜索标题或地点" clearable style="max-width: 320px" />
      <el-button :loading="loading" @click="load">刷新</el-button>
      <span class="toolbar-note">{{ visible.length }} 项 · 北京时间</span>
    </template>

    <el-table-column prop="title" label="活动名称" min-width="200" />
    <el-table-column label="类型" min-width="120">
      <template #default="{ row }">{{ kindLabel(row.kind) }}</template>
    </el-table-column>
    <el-table-column label="开始时间" min-width="170">
      <template #default="{ row }">{{ row.start_time.replace('T', ' ').slice(0, 16) }}</template>
    </el-table-column>
    <el-table-column label="报名" width="90">
      <template #default="{ row }">{{ row.booked }} / {{ row.capacity }}</template>
    </el-table-column>
    <el-table-column label="状态" width="110">
      <template #default="{ row }">
        <ZyStatusTag :dict="PROGRAM_STATUS" :value="row.status" />
      </template>
    </el-table-column>
    <el-table-column label="操作" width="200" fixed="right">
      <template #default="{ row }">
        <el-button link type="primary" @click="edit(row as Program)">编辑</el-button>
        <el-button link type="primary" @click="showAttendees(row as Program)">报名与签到</el-button>
        <el-dropdown trigger="click">
          <el-button link type="primary">更多</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="row.kind === 'COURSE'" @click="courseId = row.id; courseDialog = true">
                课节
              </el-dropdown-item>
              <el-dropdown-item @click="feedbackId = row.id; feedbackDialog = true">
                反馈
              </el-dropdown-item>
              <el-dropdown-item v-if="row.kind !== 'GUIDANCE'" @click="teamId = row.id; teamDialog = true">
                协作团队
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </el-table-column>

    <template #append>
      <p class="foot">本系统不能替代专业心理咨询</p>

      <el-dialog v-model="teamDialog" title="协作团队权限" width="min(650px, 94vw)" destroy-on-close>
        <ProgramTeam v-if="teamDialog" :program-id="teamId" />
      </el-dialog>

      <el-dialog v-model="messageDialog" title="本人预约交流" width="min(960px, 96vw)" destroy-on-close>
        <GuidanceMessages v-if="messageDialog" />
      </el-dialog>

      <el-dialog
        v-model="dialog"
        :title="selected ? '编辑活动' : '新建活动'"
        width="min(720px, 94vw)"
        :close-on-click-modal="false"
        :show-close="!saving"
        :close-on-press-escape="!saving"
      >
        <el-form label-position="top">
          <el-form-item v-if="!selected" label="从工作坊模板开始（可选）">
            <div class="template-row">
              <el-select v-model="templateId" placeholder="选择一个团体工作坊模板" style="flex: 1">
                <el-option v-for="t in workshopTemplates" :key="t.id" :label="t.title" :value="t.id" />
              </el-select>
              <el-button :disabled="!templateId" @click="applyTemplate">填入模板</el-button>
            </div>
            <p v-if="selectedTemplate" class="template-hint">
              {{ selectedTemplate.summary }} · {{ selectedTemplate.minutes }} 分钟 · {{ selectedTemplate.capacity }} 人<br />
              {{ selectedTemplate.safety }}
            </p>
            <p v-else-if="templateError" class="template-hint">{{ templateError }}</p>
          </el-form-item>
          <div class="form-grid">
            <el-form-item label="活动类型">
              <el-select v-model="form.kind">
                <el-option v-for="k in ['WORKSHOP', 'COURSE', 'GUIDANCE']" :key="k" :label="kindLabel(k)" :value="k" />
              </el-select>
            </el-form-item>
            <el-form-item label="发布状态">
              <el-select v-model="form.status" :disabled="editingStatus === 'CANCELLED'">
                <el-option v-for="s in allowedStatuses(editingStatus)" :key="s" :label="programStatusLabel(s)" :value="s" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="活动标题">
            <el-input v-model="form.title" maxlength="100" show-word-limit />
          </el-form-item>
          <el-form-item label="内容与安排">
            <el-input v-model="form.description" type="textarea" :rows="6" maxlength="6000" />
            <el-button link type="primary" @click="template">填入八周课程结构</el-button>
          </el-form-item>
          <div class="form-grid">
            <el-form-item label="开始时间（北京时间）">
              <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
            </el-form-item>
            <el-form-item label="结束时间（北京时间）">
              <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
            </el-form-item>
          </div>
          <el-form-item label="地点或参与方式">
            <el-input v-model="form.location" maxlength="200" />
          </el-form-item>
          <div class="form-grid">
            <el-form-item label="协同带领者（选填）">
              <el-input v-model="form.coLeader" maxlength="100" />
            </el-form-item>
            <el-form-item label="名额（一对一固定为 1）">
              <el-input-number v-model="form.capacity" :min="1" :max="200" :disabled="form.kind === 'GUIDANCE'" />
            </el-form-item>
          </div>
          <el-form-item label="准备材料（选填）">
            <el-input v-model="form.materials" type="textarea" maxlength="1000" />
          </el-form-item>
          <el-alert
            title="已有报名后不能修改日程或类型；需要调整时请取消原活动并发布新活动。"
            type="info"
            :closable="false"
          />
        </el-form>
        <template #footer>
          <el-button :disabled="saving" @click="dialog = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="attendeeDialog" title="预约处理中心" width="min(760px, 94vw)" :close-on-click-modal="!checking">
        <div class="booking-summary">
          <div>
            <span class="eyebrow">{{ attendeeTitle }}</span>
            <h2>{{ pendingCount }} 条待确认申请</h2>
            <p>一对一申请需确认后才能签到。待确认申请会占用名额。</p>
          </div>
          <el-button :loading="attendeeLoading" :disabled="!!checking" @click="reloadAttendees">刷新名单</el-button>
        </div>
        <el-alert v-if="attendeeError" :title="attendeeError" type="error" :closable="false" show-icon />
        <el-alert title="签到时间：开始前 30 分钟至结束后 24 小时。此处仅展示报名昵称。" :closable="false" />
        <el-table
          v-loading="attendeeLoading"
          :data="attendees"
          :row-class-name="attendeeRowClass"
          empty-text="暂无预约记录，发布后可邀请学生报名"
        >
          <el-table-column prop="nickname" label="参加者" min-width="140" />
          <el-table-column label="预约状态" min-width="160">
            <template #default="{ row }">
              <ZyStatusTag :dict="BOOKING_STATUS" :value="row.status" />
            </template>
          </el-table-column>
          <el-table-column label="下一步操作" min-width="220">
            <template #default="{ row }">
              <template v-if="row.status === 'PENDING'">
                <el-button type="primary" :loading="checking === row.id" :disabled="!!checking" @click="review(row as Attendee, 'CONFIRM')">
                  确认预约
                </el-button>
                <el-button type="danger" plain :disabled="!!checking" @click="review(row as Attendee, 'REJECT')">
                  拒绝
                </el-button>
              </template>
              <el-button
                v-else-if="row.status === 'BOOKED'"
                type="primary"
                link
                :loading="checking === row.id"
                :disabled="!!checking"
                @click="checkin(row as Attendee)"
              >
                确认签到
              </el-button>
              <span v-else class="settled">无需处理</span>
            </template>
          </el-table-column>
        </el-table>
      </el-dialog>

      <el-dialog v-model="feedbackDialog" title="活动反馈汇总" width="min(720px, 94vw)" destroy-on-close>
        <ProgramFeedback v-if="feedbackDialog" :program-id="feedbackId" />
      </el-dialog>

      <el-dialog v-model="courseDialog" title="课程章节" width="min(850px, 96vw)" destroy-on-close>
        <CourseLessons v-if="courseDialog" :program-id="courseId" />
      </el-dialog>
    </template>
  </ZyListPage>
</template>

<style scoped>
.booking-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px;
  background: var(--el-color-primary-light-9);
  border-radius: 14px;
  margin-bottom: 20px;
}

.booking-summary h2 {
  font-size: 24px;
  font-weight: 650;
  margin: 10px 0;
}

.booking-summary p {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.7;
}

.settled {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.status-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.status-cards button {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-start;
  background: var(--el-bg-color);
  padding: 20px;
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  transition: transform 0.15s, border-color 0.15s;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.status-cards button.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.status-cards strong {
  font-size: 26px;
}

.status-cards button:active {
  transform: scale(0.98);
}

.status-cards button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 3px;
}

@media (max-width: 600px) {
  .status-cards {
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .status-cards button {
    transition: none;
    transform: none !important;
  }
}

.eyebrow {
  /* AP：暗色下品牌 600 深档对比不足，取对比方向色板（亮更深、暗更浅） */
  color: var(--el-color-primary-dark-2);
  font-size: 11px;
  letter-spacing: 2px;
}

.toolbar-note,
.foot {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

@media (max-width: 600px) {
  .form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}

.template-row {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
  flex-wrap: wrap;
}

.template-row :deep(.el-select) {
  min-width: 240px;
}

.template-hint {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

:deep(.zy-row-located td) {
  background: var(--el-color-primary-light-9) !important;
}
</style>
