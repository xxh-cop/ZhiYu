<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import {
  ElAlert,
  ElButton,
  ElCard,
  ElColorPicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTabs,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import DataErrorBanner from '#/components/data-state/DataErrorBanner.vue';
import DataState from '#/components/data-state/DataState.vue';
import { requestClient } from '#/api/request';

type Source = {
  accessedAt: string;
  facts: string[];
  id: string;
  mediaReuse: string;
  organization: string;
  title: string;
  url: string;
};
type Tradition = {
  category: string;
  id: string;
  listedYear: number;
  materials: string[];
  name: string;
  practiceBoundary: string;
  process: string[];
  region: string;
  registryCode: string;
  safety: string[];
  shortName: string;
  sourceIds: string[];
  summary: string;
};
type Pattern = {
  designNote: string;
  detail: number;
  family: string;
  id: string;
  learningFocus: string;
  level: string;
  minutes: number;
  name: string;
  origin: string;
  palette: string[];
  sourceIds: string[];
  traditionId: string;
  variant: string;
};
type Catalog = {
  disclaimer: string;
  patterns: Pattern[];
  sources: Source[];
  traditions: Tradition[];
  version: string;
};
type DraftSnapshot = { catalog: Catalog; persisted: boolean; revision: number };
type PublishedSnapshot = { catalog: Catalog; revision: number; version: number };
type Workflow = { changed: boolean; draft: DraftSnapshot; published: PublishedSnapshot };
type VersionSummary = { actorName: string; createdAt: string; note: string; version: number };
type VersionDetail = VersionSummary & { catalog: Catalog };
type EditKind = 'pattern' | 'source' | 'tradition';

const catalog = ref<Catalog | null>(null);
const draftRevision = ref(0);
const draftPersisted = ref(false);
const publishedCatalog = ref<Catalog | null>(null);
const publishedRevision = ref(0);
const publishedVersion = ref(0);
const loading = ref(false);
const error = ref('');
const activeTab = ref<'patterns' | 'sources' | 'traditions'>('patterns');
const editVisible = ref(false);
const editKind = ref<EditKind>('pattern');
const editId = ref('');
const editError = ref('');
const saving = ref(false);
const form = reactive<any>({ palette: ['#ffffff', '#111111', '#217e76', '#bb914d'] });

const versions = ref<VersionSummary[]>([]);
const versionsLoading = ref(false);
const versionVisible = ref(false);
const previewVisible = ref(false);
const previewLoading = ref(false);
const preview = ref<VersionDetail | null>(null);
const publishVisible = ref(false);
const publishNote = ref('');
const publishing = ref(false);

const families: Record<string, string[]> = {
  stitch: ['sprig', 'petals', 'arcs', 'grid', 'seed', 'ribbon'],
  weave: ['plain', 'twill', 'basket', 'chevron', 'diamond', 'window'],
  cutout: ['bloom', 'leaves', 'window', 'fan', 'stars', 'double'],
  porcelain: ['medallion', 'border', 'sprig', 'wave', 'lattice', 'petal'],
};
const familyLabels: Record<string, string> = {
  stitch: '针线',
  weave: '编织',
  cutout: '剪刻',
  porcelain: '器面',
};
const variantOptions = computed(() => families[form.family] || []);
const traditionOptions = computed(() => catalog.value?.traditions || []);
const editTitle = computed(() =>
  editKind.value === 'pattern'
    ? '编辑纹样'
    : editKind.value === 'tradition'
      ? '编辑工艺'
      : '编辑来源',
);
const patternRows = computed(() =>
  (catalog.value?.patterns || []).map(pattern => ({
    ...pattern,
    traditionName: catalog.value?.traditions.find(item => item.id === pattern.traditionId)?.shortName || pattern.traditionId,
    familyLabel: familyLabels[pattern.family] || pattern.family,
  })),
);

function changedCount(current: any[] | undefined, baseline: any[] | undefined) {
  const currentList = current || [];
  const baselineMap = new Map((baseline || []).map(item => [item.id, JSON.stringify(item)]));
  return currentList.filter(item => baselineMap.get(item.id) !== JSON.stringify(item)).length
    + Math.max(0, (baseline?.length || 0) - currentList.length);
}

const diffStats = computed(() => ({
  patterns: changedCount(catalog.value?.patterns, publishedCatalog.value?.patterns),
  sources: changedCount(catalog.value?.sources, publishedCatalog.value?.sources),
  traditions: changedCount(catalog.value?.traditions, publishedCatalog.value?.traditions),
}));
const diffTotal = computed(() => diffStats.value.patterns + diffStats.value.traditions + diffStats.value.sources);
const diffSummary = computed(
  () => `${diffStats.value.patterns} 个纹样 · ${diffStats.value.traditions} 门工艺 · ${diffStats.value.sources} 条来源`,
);
const hasDraftChanges = computed(() => draftPersisted.value && diffTotal.value > 0);
const previewPatternRows = computed(() => {
  const source = preview.value?.catalog;
  return (source?.patterns || []).slice(0, 12).map(pattern => ({
    ...pattern,
    traditionName: source?.traditions.find(item => item.id === pattern.traditionId)?.shortName || pattern.traditionId,
  }));
});

function applyWorkflow(result: Workflow) {
  catalog.value = result.draft.catalog;
  draftRevision.value = result.draft.revision;
  draftPersisted.value = result.draft.persisted;
  publishedCatalog.value = result.published.catalog;
  publishedRevision.value = result.published.revision;
  publishedVersion.value = result.published.version;
}

async function loadVersions() {
  versionsLoading.value = true;
  try {
    versions.value = await requestClient.get<VersionSummary[]>('/admin/heritage/workflow/versions');
  } catch (event: any) {
    if (versionVisible.value) ElMessage.error(event?.message || '版本历史加载失败');
  } finally {
    versionsLoading.value = false;
  }
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const result = await requestClient.get<Workflow>('/admin/heritage/workflow');
    applyWorkflow(result);
    await loadVersions();
  } catch (event: any) {
    error.value = event?.message || '非遗内容加载失败，请重试。';
  } finally {
    loading.value = false;
  }
}

function lines(value: unknown) {
  return Array.isArray(value) ? value.join('\n') : '';
}

function comma(value: unknown) {
  return Array.isArray(value) ? value.join(', ') : '';
}

function parseLines(value: string) {
  return String(value || '')
    .split(/\r?\n/)
    .map(item => item.trim())
    .filter(Boolean);
}

function parseComma(value: string) {
  return String(value || '')
    .split(',')
    .map(item => item.trim())
    .filter(Boolean);
}

function openEdit(kind: EditKind, row: any) {
  editKind.value = kind;
  editId.value = row.id;
  editError.value = '';
  Object.keys(form).forEach(key => delete form[key]);
  if (kind === 'pattern') {
    Object.assign(form, {
      ...row,
      palette: [...row.palette],
      sourceIdsText: comma(row.sourceIds),
    });
  } else if (kind === 'tradition') {
    Object.assign(form, {
      ...row,
      sourceIdsText: comma(row.sourceIds),
      materialsText: lines(row.materials),
      processText: lines(row.process),
      safetyText: lines(row.safety),
    });
  } else {
    Object.assign(form, {
      ...row,
      factsText: lines(row.facts),
    });
  }
  editVisible.value = true;
  takeFormSnapshot();
}

/** 编辑弹窗未保存跟踪：打开时取快照，关弹窗 / 跳路由前比对拦截 */
let formSnapshot = '';
function takeFormSnapshot() {
  formSnapshot = JSON.stringify(form);
}
function isFormDirty() {
  return editVisible.value && JSON.stringify(form) !== formSnapshot;
}
async function confirmLeaveIfDirty() {
  if (!isFormDirty()) return true;
  try {
    await ElMessageBox.confirm('当前编辑尚未保存为草稿，离开后修改将丢失。', '未保存的修改', {
      type: 'warning',
      confirmButtonText: '仍要离开',
      cancelButtonText: '继续编辑',
    });
    return true;
  } catch {
    return false;
  }
}
async function handleEditBeforeClose(done: () => void) {
  if (await confirmLeaveIfDirty()) done();
}
onBeforeRouteLeave(async () => await confirmLeaveIfDirty());

function patternPayload() {
  return {
    name: String(form.name || '').trim(),
    traditionId: form.traditionId,
    family: form.family,
    variant: form.variant,
    detail: Number(form.detail),
    palette: form.palette.map((color: any) => String(color || '').toUpperCase()),
    sourceIds: parseComma(form.sourceIdsText),
    designNote: String(form.designNote || '').trim(),
    learningFocus: String(form.learningFocus || '').trim(),
    minutes: Number(form.minutes),
    level: form.level,
  };
}

function traditionPayload() {
  return {
    name: String(form.name || '').trim(),
    shortName: String(form.shortName || '').trim(),
    region: String(form.region || '').trim(),
    category: String(form.category || '').trim(),
    registryCode: String(form.registryCode || '').trim(),
    listedYear: Number(form.listedYear),
    sourceIds: parseComma(form.sourceIdsText),
    summary: String(form.summary || '').trim(),
    materials: parseLines(form.materialsText),
    process: parseLines(form.processText),
    safety: parseLines(form.safetyText),
    practiceBoundary: String(form.practiceBoundary || '').trim(),
  };
}

function sourcePayload() {
  return {
    title: String(form.title || '').trim(),
    organization: String(form.organization || '').trim(),
    url: String(form.url || '').trim(),
    accessedAt: String(form.accessedAt || '').trim(),
    mediaReuse: form.mediaReuse,
    facts: parseLines(form.factsText),
  };
}

function validateClient() {
  if (editKind.value === 'pattern') {
    const payload = patternPayload();
    if (!payload.name || !payload.traditionId || !payload.variant || !payload.designNote || !payload.learningFocus) {
      return '请填写纹样名称、所属工艺、变体、设计说明和学习观察点。';
    }
    if (payload.palette.some((color: string) => !/^#[0-9a-f]{6}$/i.test(color))) return '色板必须是四个十六进制颜色。';
    if (payload.sourceIds.length === 0) return '至少填写一个来源 ID。';
  }
  if (editKind.value === 'tradition') {
    const payload = traditionPayload();
    if (!payload.name || !payload.shortName || !payload.region || !payload.registryCode || !payload.practiceBoundary) {
      return '请填写工艺名称、短名、地域、编号和练习边界。';
    }
    if (payload.sourceIds.length === 0) return '至少填写一个来源 ID。';
  }
  if (editKind.value === 'source') {
    const payload = sourcePayload();
    if (!payload.title || !payload.organization || !payload.url || !payload.accessedAt) return '请填写来源标题、机构、链接和访问日期。';
    if (!/^https:\/\//.test(payload.url)) return '来源链接必须以 https:// 开头。';
    if (payload.facts.length === 0) return '至少填写一条已核实事实。';
  }
  return '';
}

async function save() {
  if (!catalog.value || saving.value) return;
  editError.value = validateClient();
  if (editError.value) return;
  const next: Catalog = JSON.parse(JSON.stringify(catalog.value));
  if (editKind.value === 'pattern') {
    const index = next.patterns.findIndex(item => item.id === editId.value);
    const current = next.patterns[index];
    if (!current) return;
    next.patterns[index] = { ...current, ...patternPayload() } as Pattern;
  } else if (editKind.value === 'tradition') {
    const index = next.traditions.findIndex(item => item.id === editId.value);
    const current = next.traditions[index];
    if (!current) return;
    next.traditions[index] = { ...current, ...traditionPayload() } as Tradition;
  } else {
    const index = next.sources.findIndex(item => item.id === editId.value);
    const current = next.sources[index];
    if (!current) return;
    next.sources[index] = { ...current, ...sourcePayload() } as Source;
  }
  saving.value = true;
  try {
    const result = await requestClient.put<Workflow>('/admin/heritage/workflow/draft', {
      revision: draftRevision.value,
      catalog: next,
    });
    applyWorkflow(result);
    editVisible.value = false;
    ElMessage.success('已保存到草稿，发布后学生端才会更新');
  } catch (event: any) {
    if (event?.code === 3001) {
      editVisible.value = false;
      await load();
      ElMessage.error('草稿已被其他管理员更新，已刷新，请重新编辑');
    } else {
      editError.value = event?.message || '保存失败，请检查内容后重试';
    }
  } finally {
    saving.value = false;
  }
}

function openPublish() {
  publishNote.value = '';
  publishVisible.value = true;
}

async function publish() {
  if (publishing.value || !hasDraftChanges.value) return;
  publishing.value = true;
  try {
    const result = await requestClient.post<Workflow>('/admin/heritage/workflow/publish', {
      revision: draftRevision.value,
      note: publishNote.value.trim() || null,
    });
    applyWorkflow(result);
    publishVisible.value = false;
    await loadVersions();
    ElMessage.success(`已发布 v${result.published.version}，学生端已生效`);
  } catch (event: any) {
    if (event?.code === 3001) {
      publishVisible.value = false;
      await load();
      ElMessage.error('草稿已变化或被其他管理员处理，已刷新');
    } else {
      ElMessage.error(event?.message || '发布失败，请重试');
    }
  } finally {
    publishing.value = false;
  }
}

async function discardDraft() {
  try {
    await ElMessageBox.confirm('将删除未发布的草稿，学生端已发布内容不受影响。', '放弃草稿', {
      type: 'warning',
      confirmButtonText: '放弃草稿',
    });
    const result = await requestClient.delete<Workflow>('/admin/heritage/workflow/draft');
    applyWorkflow(result);
    ElMessage.success('草稿已放弃');
  } catch (event: any) {
    if (event !== 'cancel' && event !== 'close') ElMessage.error(event?.message || '操作失败');
  }
}

async function unpublish() {
  try {
    await ElMessageBox.confirm(
      `学生端将立即回到内置目录，当前草稿也会清空；已发布版本会保留在版本历史中。`,
      '下架非遗目录',
      { type: 'warning', confirmButtonText: '确认下架' },
    );
    const result = await requestClient.post<Workflow>('/admin/heritage/workflow/unpublish', {
      note: '管理端下架',
    });
    applyWorkflow(result);
    await loadVersions();
    ElMessage.success('已下架，学生端回到内置目录');
  } catch (event: any) {
    if (event !== 'cancel' && event !== 'close') ElMessage.error(event?.message || '下架失败');
  }
}

async function reset() {
  try {
    await ElMessageBox.confirm('将清空草稿与已发布覆盖，恢复内置 24 份原创教学构图。历史版本仍保留。', '恢复内置目录', {
      type: 'warning',
      confirmButtonText: '恢复内置',
    });
    const result = await requestClient.delete<Workflow>('/admin/heritage/workflow/published');
    applyWorkflow(result);
    ElMessage.success('已恢复内置目录');
  } catch (event: any) {
    if (event !== 'cancel' && event !== 'close') ElMessage.error(event?.message || '恢复失败');
  }
}

async function openVersions() {
  versionVisible.value = true;
  await loadVersions();
}

async function previewVersion(row: any) {
  previewLoading.value = true;
  previewVisible.value = true;
  preview.value = null;
  try {
    preview.value = await requestClient.get<VersionDetail>(
      `/admin/heritage/workflow/versions/${row.version}`,
    );
  } catch (event: any) {
    previewVisible.value = false;
    ElMessage.error(event?.message || '版本内容加载失败');
  } finally {
    previewLoading.value = false;
  }
}

async function rollback(row: any) {
  try {
    const { value } = await ElMessageBox.prompt(
      `将把 v${row.version} 的内容重新发布为新版本，学生端立即生效并清空当前草稿。历史版本不会被改写。`,
      '回滚到历史版本',
      {
        confirmButtonText: '确认回滚',
        inputPlaceholder: '可选：填写回滚原因',
        inputValidator: (input: string) => !input || input.length <= 200 || '原因不能超过 200 字',
      },
    );
    const result = await requestClient.post<Workflow>('/admin/heritage/workflow/rollback', {
      version: row.version,
      note: value?.trim() || null,
    });
    applyWorkflow(result);
    previewVisible.value = false;
    await loadVersions();
    ElMessage.success(`已回滚，生成新版本 v${result.published.version}`);
  } catch (event: any) {
    if (event !== 'cancel' && event !== 'close') ElMessage.error(event?.message || '回滚失败');
  }
}

onMounted(load);
</script>

<template>
  <div class="zy-business-page heritage-admin-page flex flex-col gap-4">
    <BusinessPageHeader
      title="非遗内容"
      description="维护工艺、纹样与来源；先保存草稿，发布后学生端才会生效。"
      :loading="loading"
      @refresh="load"
    >
      <div class="flex flex-wrap gap-2">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="openVersions">版本历史</el-button>
        <el-button type="danger" plain :disabled="publishedVersion === 0" @click="unpublish">下架</el-button>
        <el-button type="danger" plain :disabled="publishedVersion === 0 && !draftPersisted" @click="reset">恢复内置目录</el-button>
      </div>
    </BusinessPageHeader>

    <el-alert
      title="草稿不会影响学生端；发布时生成不可变版本，可随时查看历史或回滚，下架后学生端回到内置目录。"
      type="info"
      :closable="false"
      show-icon
    />
    <DataErrorBanner v-if="error && catalog" :error="error" :retrying="loading" @retry="load" />

    <div class="workflow-strip">
      <div class="workflow-metric">
        <span class="workflow-label">学生端当前</span>
        <strong>{{ publishedVersion > 0 ? `已发布 v${publishedVersion}` : '内置目录' }}</strong>
        <small>{{ publishedVersion > 0 ? `发布配置 revision ${publishedRevision}` : '尚未发布管理端内容' }}</small>
      </div>
      <div class="workflow-metric">
        <span class="workflow-label">编辑草稿</span>
        <strong>{{ draftPersisted ? `草稿 v${draftRevision}` : '未保存草稿' }}</strong>
        <small>{{ hasDraftChanges ? '与已发布内容有差异，待发布' : draftPersisted ? '与已发布内容一致' : '编辑后会先保存为草稿' }}</small>
      </div>
      <div class="workflow-metric">
        <span class="workflow-label">待发布差异</span>
        <strong>{{ hasDraftChanges ? diffSummary : '无变化' }}</strong>
        <small>{{ publishedCatalog?.patterns.length || 0 }} 份原创教学构图作为对比基线</small>
      </div>
      <div class="workflow-actions">
        <el-button v-if="draftPersisted" plain @click="discardDraft">放弃草稿</el-button>
        <el-button type="primary" :disabled="!hasDraftChanges" @click="openPublish">发布草稿</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <div class="heritage-summary">
        <div>
          <strong>{{ catalog?.patterns.length || 0 }} 份原创教学构图</strong>
          <span>{{ catalog?.traditions.length || 0 }} 门工艺 · {{ catalog?.sources.length || 0 }} 条来源</span>
        </div>
        <div class="heritage-tags">
          <el-tag :type="publishedVersion > 0 ? 'success' : 'info'">
            {{ publishedVersion > 0 ? `已发布 v${publishedVersion}` : '内置目录' }}
          </el-tag>
          <el-tag v-if="draftPersisted" type="warning">草稿 v{{ draftRevision }}</el-tag>
          <el-tag v-if="hasDraftChanges" type="danger" effect="plain">未发布修改</el-tag>
        </div>
      </div>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="纹样" name="patterns">
          <el-table v-loading="loading" :data="patternRows">
            <el-table-column prop="name" label="纹样" min-width="140" />
            <el-table-column prop="traditionName" label="工艺" min-width="120" />
            <el-table-column label="结构" min-width="120">
              <template #default="{ row }">{{ row.familyLabel }} / {{ row.variant }}</template>
            </el-table-column>
            <el-table-column prop="level" label="等级" width="90" />
            <el-table-column prop="minutes" label="分钟" width="80" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit('pattern', row)">编辑</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <DataState :loading="loading" :error="error" empty-text="暂无纹样" @retry="load" />
            </template>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="工艺" name="traditions">
          <el-table v-loading="loading" :data="catalog?.traditions || []">
            <el-table-column prop="name" label="正式名称" min-width="180" />
            <el-table-column prop="shortName" label="短名" min-width="110" />
            <el-table-column prop="region" label="地域" width="100" />
            <el-table-column prop="registryCode" label="编号" width="90" />
            <el-table-column prop="listedYear" label="列入年份" width="100" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit('tradition', row)">编辑</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <DataState :loading="loading" :error="error" empty-text="暂无工艺" @retry="load" />
            </template>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="来源" name="sources">
          <el-table v-loading="loading" :data="catalog?.sources || []">
            <el-table-column prop="title" label="来源" min-width="180" />
            <el-table-column prop="organization" label="机构" min-width="220" />
            <el-table-column prop="mediaReuse" label="媒体再利用" width="120" />
            <el-table-column prop="accessedAt" label="访问日期" width="120" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit('source', row)">编辑</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <DataState :loading="loading" :error="error" empty-text="暂无来源" @retry="load" />
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
    <p class="business-note">保存前请确认来源与原创边界；不要粘贴未授权图片、未核实传承人资料或诊断性措辞。</p>

    <el-dialog v-model="editVisible" :title="editTitle" width="min(760px, 94vw)" destroy-on-close :before-close="handleEditBeforeClose">
      <el-form label-position="top" class="heritage-form">
        <template v-if="editKind === 'pattern'">
          <div class="heritage-form-grid">
            <el-form-item label="纹样名称"><el-input v-model="form.name" maxlength="40" /></el-form-item>
            <el-form-item label="所属工艺">
              <el-select v-model="form.traditionId">
                <el-option v-for="tradition in traditionOptions" :key="tradition.id" :label="tradition.shortName" :value="tradition.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="结构族">
              <el-select v-model="form.family" @change="form.variant = variantOptions[0]">
                <el-option v-for="(label, key) in familyLabels" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
            <el-form-item label="变体">
              <el-select v-model="form.variant">
                <el-option v-for="variant in variantOptions" :key="variant" :label="variant" :value="variant" />
              </el-select>
            </el-form-item>
            <el-form-item label="细节密度"><el-input-number v-model="form.detail" :min="4" :max="12" /></el-form-item>
            <el-form-item label="数字练习分钟"><el-input-number v-model="form.minutes" :min="1" :max="120" /></el-form-item>
            <el-form-item label="等级">
              <el-select v-model="form.level">
                <el-option label="入门" value="入门" />
                <el-option label="进阶" value="进阶" />
              </el-select>
            </el-form-item>
            <el-form-item label="来源 ID（逗号分隔）"><el-input v-model="form.sourceIdsText" /></el-form-item>
          </div>
          <el-form-item label="色板（底 / 墨 / 主 / 辅）">
            <div class="palette-row">
              <el-color-picker v-for="(_, index) in form.palette" :key="index" v-model="form.palette[index]" />
            </div>
          </el-form-item>
          <el-form-item label="学习观察点"><el-input v-model="form.learningFocus" type="textarea" :rows="3" maxlength="300" show-word-limit /></el-form-item>
          <el-form-item label="原创设计说明"><el-input v-model="form.designNote" type="textarea" :rows="4" maxlength="600" show-word-limit /></el-form-item>
        </template>

        <template v-else-if="editKind === 'tradition'">
          <div class="heritage-form-grid">
            <el-form-item label="正式名称"><el-input v-model="form.name" maxlength="80" /></el-form-item>
            <el-form-item label="短名"><el-input v-model="form.shortName" maxlength="30" /></el-form-item>
            <el-form-item label="地域"><el-input v-model="form.region" maxlength="30" /></el-form-item>
            <el-form-item label="类别"><el-input v-model="form.category" maxlength="30" /></el-form-item>
            <el-form-item label="名录编号"><el-input v-model="form.registryCode" maxlength="20" /></el-form-item>
            <el-form-item label="列入年份"><el-input-number v-model="form.listedYear" :min="1900" :max="2100" /></el-form-item>
            <el-form-item label="来源 ID（逗号分隔）"><el-input v-model="form.sourceIdsText" /></el-form-item>
          </div>
          <el-form-item label="简介"><el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
          <el-form-item label="材料（一行一项）"><el-input v-model="form.materialsText" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="工序（一行一项）"><el-input v-model="form.processText" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="安全提示（一行一项）"><el-input v-model="form.safetyText" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="数字练习与实物工艺边界"><el-input v-model="form.practiceBoundary" type="textarea" :rows="4" maxlength="600" show-word-limit /></el-form-item>
        </template>

        <template v-else>
          <el-form-item label="来源标题"><el-input v-model="form.title" maxlength="120" /></el-form-item>
          <el-form-item label="机构"><el-input v-model="form.organization" maxlength="120" /></el-form-item>
          <el-form-item label="链接（https）"><el-input v-model="form.url" maxlength="500" /></el-form-item>
          <div class="heritage-form-grid">
            <el-form-item label="访问日期"><el-input v-model="form.accessedAt" placeholder="YYYY-MM-DD" maxlength="10" /></el-form-item>
            <el-form-item label="媒体再利用">
              <el-select v-model="form.mediaReuse">
                <el-option label="未授予" value="NOT_GRANTED" />
                <el-option label="已授予" value="GRANTED" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="已核实事实（一行一条）"><el-input v-model="form.factsText" type="textarea" :rows="5" /></el-form-item>
        </template>

        <el-alert v-if="editError" :title="editError" type="error" :closable="false" show-icon />
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存到草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="publishVisible" title="发布非遗目录草稿" width="min(620px, 94vw)" destroy-on-close>
      <el-alert
        :title="`发布会生成 v${publishedVersion + 1}，学生端立即使用新内容。`"
        type="warning"
        :closable="false"
        show-icon
      />
      <div class="publish-diff">
        <span>本次变更</span>
        <strong>{{ diffSummary }}</strong>
      </div>
      <el-input
        v-model="publishNote"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="可选：填写本次发布说明，便于回滚时识别"
      />
      <template #footer>
        <el-button :disabled="publishing" @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="publish">确认发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="versionVisible" title="发布版本" width="min(860px, 94vw)">
      <el-table v-loading="versionsLoading" :data="versions">
        <el-table-column label="版本" width="90">
          <template #default="{ row }">
            <el-tag :type="row.version === publishedVersion ? 'success' : 'info'" effect="plain">
              v{{ row.version }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="发布说明" min-width="180" show-overflow-tooltip />
        <el-table-column prop="actorName" label="操作人" width="110" />
        <el-table-column prop="createdAt" label="发布时间" width="150" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="previewVersion(row)">查看</el-button>
            <el-button link type="warning" @click="rollback(row)">回滚</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <DataState :loading="versionsLoading" error="" empty-text="暂无发布版本" />
        </template>
      </el-table>
      <p class="business-note">回滚会生成新的发布版本，不删除或改写历史记录。</p>
    </el-dialog>

    <el-dialog
      v-model="previewVisible"
      :title="preview ? `版本 v${preview.version}` : '版本内容'"
      width="min(920px, 94vw)"
    >
      <div class="version-preview">
        <el-skeleton v-if="previewLoading" :rows="5" animated />
        <div v-if="preview" class="version-meta">
          <span>{{ preview.note || '无发布说明' }}</span>
          <span>{{ preview.actorName }} · {{ preview.createdAt }}</span>
        </div>
        <el-table :data="previewPatternRows" max-height="380">
          <el-table-column prop="name" label="纹样" min-width="130" />
          <el-table-column prop="traditionName" label="工艺" width="100" />
          <el-table-column prop="level" label="等级" width="80" />
          <el-table-column prop="minutes" label="分钟" width="80" />
          <el-table-column prop="learningFocus" label="学习观察点" min-width="260" show-overflow-tooltip />
        </el-table>
        <p class="business-note">预览展示前 12 个纹样；回滚会发布该版本的完整内容。</p>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button v-if="preview" type="warning" @click="rollback(preview)">回滚到此版本</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.heritage-admin-page { max-width: 1280px; margin: 0 auto; }
.heritage-summary { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 14px; flex-wrap: wrap; }
.heritage-summary strong { display: block; font-size: 17px; color: var(--el-text-color-primary); }
.heritage-summary > div > span { display: block; margin-top: 5px; font-size: 13px; color: var(--el-text-color-regular); }
.heritage-tags { display: flex; gap: 8px; flex-wrap: wrap; }
.workflow-strip {
  position: sticky;
  top: 8px;
  z-index: 10;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
  gap: 18px;
  align-items: center;
  padding: 18px 20px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  /* 吸顶需实底避免下方内容透出；两侧留白处仍透出环境光 */
  background: var(--el-bg-color);
  box-shadow: 0 8px 24px -18px hsl(var(--foreground) / 35%);
}
.workflow-metric { min-width: 0; }
.workflow-label { display: block; font-size: 12px; letter-spacing: 0.04em; color: var(--el-text-color-secondary); }
.workflow-metric strong { display: block; margin-top: 5px; font-size: 16px; color: var(--el-text-color-primary); }
.workflow-metric small { display: block; margin-top: 4px; font-size: 12px; line-height: 1.6; color: var(--el-text-color-regular); }
.workflow-actions { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.publish-diff { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 14px 0; padding: 12px 14px; border-radius: 8px; background: var(--el-fill-color-light); }
.publish-diff span { font-size: 13px; color: var(--el-text-color-regular); }
.publish-diff strong { font-size: 14px; color: var(--el-text-color-primary); }
.version-meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; font-size: 13px; color: var(--el-text-color-regular); flex-wrap: wrap; }
.heritage-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.palette-row { display: flex; gap: 14px; flex-wrap: wrap; }
.heritage-form :deep(.el-select) { width: 100%; }
@media (max-width: 960px) {
  .workflow-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .workflow-actions { grid-column: 1 / -1; justify-content: flex-start; }
}
@media (max-width: 640px) {
  .heritage-form-grid { grid-template-columns: minmax(0, 1fr); }
  .heritage-summary { align-items: flex-start; }
  .workflow-strip { grid-template-columns: minmax(0, 1fr); }
}
</style>
