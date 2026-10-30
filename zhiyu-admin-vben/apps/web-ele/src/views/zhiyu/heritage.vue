<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
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
type Snapshot = { catalog: Catalog; revision: number };
type EditKind = 'pattern' | 'source' | 'tradition';

const catalog = ref<Catalog | null>(null);
const revision = ref(0);
const loading = ref(false);
const error = ref('');
const activeTab = ref<'patterns' | 'sources' | 'traditions'>('patterns');
const editVisible = ref(false);
const editKind = ref<EditKind>('pattern');
const editId = ref('');
const editError = ref('');
const saving = ref(false);
const form = reactive<any>({ palette: ['#ffffff', '#111111', '#2f7566', '#bb914d'] });

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

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const result = await requestClient.get<Snapshot>('/admin/heritage/catalog');
    catalog.value = result.catalog;
    revision.value = result.revision;
  } catch (event: any) {
    error.value = event?.message || '非遗目录加载失败，请重试。';
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
}

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
    const result = await requestClient.put<Snapshot>('/admin/heritage/catalog', {
      revision: revision.value,
      catalog: next,
    });
    catalog.value = result.catalog;
    revision.value = result.revision;
    editVisible.value = false;
    ElMessage.success('已保存，学生端将使用新内容');
  } catch (event: any) {
    if (event?.code === 3001) {
      editVisible.value = false;
      await load();
      ElMessage.error('目录已被其他管理员更新，已刷新，请重新编辑');
    } else {
      editError.value = event?.message || '保存失败，请检查内容后重试';
    }
  } finally {
    saving.value = false;
  }
}

async function reset() {
  try {
    await ElMessageBox.confirm('将删除管理端保存的覆盖，恢复内置 24 份原创教学构图。此操作会立即影响学生端。', '恢复内置目录', {
      type: 'warning',
      confirmButtonText: '恢复内置',
    });
    const result = await requestClient.delete<Snapshot>('/admin/heritage/catalog');
    catalog.value = result.catalog;
    revision.value = result.revision;
    ElMessage.success('已恢复内置目录');
  } catch (event: any) {
    if (event !== 'cancel' && event !== 'close') ElMessage.error(event?.message || '恢复失败');
  }
}

onMounted(load);
</script>

<template>
  <div class="zy-business-page heritage-admin-page flex flex-col gap-4">
    <BusinessPageHeader
      title="非遗内容"
      description="维护工艺、纹样与来源说明；保存后立即对已登录学生生效。"
      :loading="loading"
      @refresh="load"
    >
      <div class="flex flex-wrap gap-2">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="danger" plain :disabled="!catalog || revision === 0" @click="reset">恢复内置目录</el-button>
      </div>
    </BusinessPageHeader>

    <el-alert
      title="当前维护方案：整份目录保存到 runtime_config，带 revision 冲突保护与管理员操作记录；不新增数据库表。"
      type="info"
      :closable="false"
      show-icon
    />
    <DataErrorBanner v-if="error && catalog" :error="error" :retrying="loading" @retry="load" />
    <el-card shadow="never">
      <div class="heritage-summary">
        <div>
          <strong>{{ catalog?.patterns.length || 0 }} 份原创教学构图</strong>
          <span>{{ catalog?.traditions.length || 0 }} 门工艺 · {{ catalog?.sources.length || 0 }} 条来源</span>
        </div>
        <el-tag type="success">当前版本 v{{ revision }} · 保存即生效</el-tag>
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

    <el-dialog v-model="editVisible" :title="editTitle" width="min(760px, 94vw)" destroy-on-close>
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
        <el-button type="primary" :loading="saving" @click="save">保存并生效</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.heritage-admin-page { max-width: 1280px; margin: 0 auto; }
.heritage-summary { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 14px; flex-wrap: wrap; }
.heritage-summary strong { display: block; font-size: 17px; color: var(--el-text-color-primary); }
.heritage-summary span { display: block; margin-top: 5px; font-size: 13px; color: var(--el-text-color-regular); }
.heritage-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.palette-row { display: flex; gap: 14px; flex-wrap: wrap; }
.heritage-form :deep(.el-select) { width: 100%; }
@media (max-width: 640px) {
  .heritage-form-grid { grid-template-columns: minmax(0, 1fr); }
  .heritage-summary { align-items: flex-start; }
}
</style>
