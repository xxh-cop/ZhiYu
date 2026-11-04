<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import type { FormInstance, FormRules } from 'element-plus';

import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@vben/stores';

import {
  addMaterial,
  addStep,
  createProject,
  deleteMaterial,
  deleteStep,
  getProjectDetail,
  updateProject,
  updateStep,
  type InheritorApi,
} from '#/api/zhiyu/inheritor';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import { PROJECT_STATUS } from '#/constants/status-dict';
import { textRules } from '#/utils/form-rules';
import { focusFirstError } from '#/utils/form-a11y';

import AudioUpload from './components/AudioUpload.vue';
import CraftSafetyEditor from './components/CraftSafetyEditor.vue';
import VideoUpload from './components/VideoUpload.vue';

const videoBusy = ref(false);
const audioBusy = ref(false);

/** AW2（2026-11-05）：音频上传仅传承人（role=1）与管理员（role=3），与后端契约一致 */
const userStore = useUserStore();
const canUploadAudio = computed(() => {
  const roles = userStore.userInfo?.roles ?? [];
  return roles.includes('ROLE_1') || roles.includes('ROLE_3');
});

const route = useRoute();
const router = useRouter();

/**
 * 从路由参数中提取项目 ID（project-edit/:id 有 id，project-new 无）。
 * 保持字符串类型，避免 Number() 导致雪花算法大整数精度丢失。
 */
const projectId = computed<string | null>(() => {
  const id = route.params.id;
  return id ? String(id) : null;
});
const isNew = computed(() => projectId.value === null);

/** 活跃 Tab */
const activeTab = ref('basic');

/** 加载状态 */
const loading = ref(false);
const saving = ref(false);

/** 项目数据 */
const project = ref<InheritorApi.ProjectDetail | null>(null);

/** 基础信息表单 */
const form = ref<InheritorApi.ProjectCreateBody>({
  title: '',
  intro: '',
  cultureBg: '',
  level: 1,
  estMinutes: 60,
  category: '',
  therapyTags: '',
  coverUrl: '',
  videoUrl: '',
});

/** 步骤列表 */
const steps = ref<InheritorApi.StepItem[]>([]);

/** 材料列表 */
const materials = ref<InheritorApi.MaterialItem[]>([]);

/** 是否可编辑（仅 status=0 或新建时可编辑） */
const editable = computed(() => {
  if (isNew.value) return true;
  return project.value?.status === 0;
});

/** 状态标签统一取状态字典 PROJECT_STATUS */

/** 难度选项（文案与状态字典 PROJECT_LEVEL 一致，level=3 定稿「高阶」对齐 DDL/契约/学生端） */
const levelOptions = [
  { value: 1, label: '入门' },
  { value: 2, label: '进阶' },
  { value: 3, label: '高阶' },
];

// ── 步骤弹窗 ──
const stepDialogVisible = ref(false);
const stepDialogMode = ref<'add' | 'edit'>('add');
const stepEditingId = ref<number | string | null>(null);
const stepFormRef = ref<FormInstance>();
const stepForm = ref<InheritorApi.StepBody>({
  stepNo: 1,
  title: '',
  guide: '',
  audioUrl: '',
});

function openAddStepDialog() {
  stepDialogMode.value = 'add';
  stepEditingId.value = null;
  const maxStepNo = steps.value.reduce(
    (max, s) => Math.max(max, s.stepNo),
    0,
  );
  stepForm.value = {
    stepNo: maxStepNo + 1,
    title: '',
    guide: '',
    audioUrl: '',
  };
  stepDialogVisible.value = true;
}

function openEditStepDialog(step: InheritorApi.StepItem) {
  stepDialogMode.value = 'edit';
  stepEditingId.value = step.id;
  stepForm.value = {
    stepNo: step.stepNo,
    title: step.title,
    guide: step.guide || '',
    audioUrl: step.audioUrl || '',
  };
  stepDialogVisible.value = true;
}

async function handleSaveStep() {
  if (!projectId.value) return;
  if (audioBusy.value) {
    ElMessage.warning('请等待音频上传完成');
    return;
  }
  if (stepFormRef.value) {
    const valid = await stepFormRef.value.validate().catch(() => false);
    if (!valid) return;
  }
  try {
    if (stepDialogMode.value === 'add') {
      const res = await addStep(projectId.value!, stepForm.value);
      steps.value.push({
        id: res.id,
        ...stepForm.value,
      });
      ElMessage.success('步骤添加成功');
    } else if (stepEditingId.value) {
      await updateStep(projectId.value!, stepEditingId.value!, stepForm.value);
      const idx = steps.value.findIndex(
        (s) => s.id === stepEditingId.value,
      );
      if (idx !== -1) {
        steps.value[idx] = {
          id: stepEditingId.value,
          ...stepForm.value,
        };
      }
      ElMessage.success('步骤更新成功');
    }
    stepDialogVisible.value = false;
  } catch {
    // 错误由全局拦截器处理
  }
}

async function handleDeleteStep(step: InheritorApi.StepItem) {
  if (!projectId.value) return;
  try {
    await ElMessageBox.confirm(
      `确认删除步骤「${step.title}」？`,
      '删除确认',
      { type: 'warning' },
    );
    await deleteStep(projectId.value!, step.id);
    steps.value = steps.value.filter((s) => s.id !== step.id);
    ElMessage.success('步骤删除成功');
  } catch {
    // 用户取消或请求失败
  }
}

// ── 材料弹窗 ──
const materialDialogVisible = ref(false);
const materialFormRef = ref<FormInstance>();
const materialForm = ref<InheritorApi.MaterialBody>({
  name: '',
  qty: '',
});

function openAddMaterialDialog() {
  materialForm.value = { name: '', qty: '' };
  materialDialogVisible.value = true;
}

async function handleSaveMaterial() {
  if (!projectId.value) return;
  if (materialFormRef.value) {
    const valid = await materialFormRef.value.validate().catch(() => false);
    if (!valid) return;
  }
  try {
    const res = await addMaterial(projectId.value!, materialForm.value);
    materials.value.push({
      id: res.id,
      ...materialForm.value,
    });
    ElMessage.success('材料添加成功');
    materialDialogVisible.value = false;
  } catch {
    // 错误由全局拦截器处理
  }
}

async function handleDeleteMaterial(material: InheritorApi.MaterialItem) {
  if (!projectId.value) return;
  try {
    await ElMessageBox.confirm(
      `确认删除材料「${material.name}」？`,
      '删除确认',
      { type: 'warning' },
    );
    await deleteMaterial(projectId.value!, material.id);
    materials.value = materials.value.filter((m) => m.id !== material.id);
    ElMessage.success('材料删除成功');
  } catch {
    // 用户取消或请求失败
  }
}

// ── 保存基础信息 ──
const basicFormRef = ref<FormInstance>();
/** 基础信息校验：规则共享自 #/utils/form-rules，错误就地 + scroll-to-error 聚焦 */
const basicRules: FormRules = {
  title: textRules('项目标题', { max: 100 }),
  level: [{ required: true, message: '请选择难度等级', trigger: 'change' }],
};

async function handleSaveBasic() {
  if (videoBusy.value) {
    ElMessage.warning('请等待视频上传完成');
    return;
  }
  if (basicFormRef.value) {
    const valid = await basicFormRef.value.validate().catch(() => false);
    if (!valid) {
      // scroll-to-error 负责滚动，focusFirstError 补键盘焦点
      await focusFirstError();
      return;
    }
  }
  saving.value = true;
  try {
    if (isNew.value) {
      const res = await createProject(form.value);
      ElMessage.success('项目创建成功');
      // res.id 是字符串，直接用于 URL，不做 Number 转换
      router.replace(`/my-inheritance/project-edit/${res.id}`);
    } else if (projectId.value) {
      await updateProject(projectId.value!, form.value);
      ElMessage.success('保存成功');
    }
  } catch {
    // 错误由全局拦截器处理
  } finally {
    saving.value = false;
  }
}

/** 加载项目详情 */
async function fetchDetail() {
  if (!projectId.value) return;
  loading.value = true;
  try {
    const data = await getProjectDetail(projectId.value!);
    project.value = data;
    form.value = {
      title: data.title || '',
      intro: data.intro || '',
      cultureBg: data.cultureBg || '',
      level: data.level || 1,
      estMinutes: data.estMinutes || 60,
      category: data.category || '',
      therapyTags: data.therapyTags || '',
      coverUrl: data.coverUrl || '',
      videoUrl: data.videoUrl || '',
    };
    steps.value = data.steps || [];
    materials.value = data.materials || [];
  } catch {
    // 错误由全局拦截器处理
  } finally {
    loading.value = false;
  }
}

/** 返回列表 */
function handleBack() {
  router.push('/my-inheritance/projects');
}

onMounted(() => {
  if (!isNew.value) {
    fetchDetail();
  }
});

// 路由参数变化时重新加载（从 new 跳转到 edit 后）
watch(projectId, (newId) => {
  if (newId !== null) {
    fetchDetail();
  }
});
</script>

<template>
  <div v-loading="loading" class="zy-business-page flex flex-col gap-4">
    <!-- 顶部标题栏 -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-3">
        <el-button text @click="handleBack">← 返回</el-button>
        <h2 class="m-0 text-xl font-semibold">
          {{ isNew ? '新建项目' : '编辑项目' }}
        </h2>
        <ZyStatusTag
          v-if="!isNew && project"
          :dict="PROJECT_STATUS"
          :value="project.status"
        />
      </div>
      <el-button
        v-if="activeTab === 'basic'"
        type="primary"
        :loading="saving"
        :disabled="!editable"
        @click="handleSaveBasic"
      >
        {{ isNew ? '创建项目' : '保存' }}
      </el-button>
    </div>

    <!-- 非待审状态提示 -->
    <el-alert
      v-if="!isNew && !editable"
      title="该项目已上架或下架，基础信息暂不可编辑；仍可维护材料安全说明"
      type="info"
      :closable="false"
      show-icon
    />

    <!-- Tab 区域 -->
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane v-if="projectId" label="材料安全" name="safety">
          <CraftSafetyEditor :project-id="projectId" />
        </el-tab-pane>
        <!-- Tab 1: 基础信息 -->
        <el-tab-pane label="基础信息" name="basic">
          <el-form
            ref="basicFormRef"
            :model="form"
            :rules="basicRules"
            label-width="100px"
            :disabled="!editable"
            scroll-to-error
            class="max-w-2xl"
          >
            <h3 class="form-section-title">内容信息</h3>
            <el-form-item label="项目标题" prop="title">
              <el-input
                v-model="form.title"
                placeholder="请输入项目标题"
                maxlength="100"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="简介">
              <el-input
                v-model="form.intro"
                type="textarea"
                :rows="3"
                placeholder="简要介绍项目内容"
              />
            </el-form-item>
            <el-form-item label="文化背景">
              <el-input
                v-model="form.cultureBg"
                type="textarea"
                :rows="3"
                placeholder="介绍该非遗项目的历史文化背景"
              />
            </el-form-item>
            <h3 class="form-section-title">教学设置</h3>
            <el-form-item label="难度等级" prop="level">
              <el-select v-model="form.level" style="width: 160px">
                <el-option
                  v-for="opt in levelOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="预计时长">
              <el-input-number
                v-model="form.estMinutes"
                :min="1"
                :max="600"
                :step="5"
                style="width: 160px"
              />
              <span class="ml-2 text-sm muted-inline">分钟</span>
            </el-form-item>
            <el-form-item label="品类">
              <el-input
                v-model="form.category"
                placeholder="如：赣绣、扎染、陶艺"
                style="width: 220px"
              />
            </el-form-item>
            <el-form-item label="疗愈标签">
              <el-input
                v-model="form.therapyTags"
                placeholder="如：减压、专注、情绪表达"
                style="width: 320px"
              />
            </el-form-item>
            <h3 class="form-section-title">教学资源</h3>
            <el-form-item label="教学视频">
              <VideoUpload v-model="form.videoUrl" :disabled="!editable" @busy="videoBusy = $event" />
            </el-form-item>
            <el-form-item label="封面图 URL">
              <el-input
                v-model="form.coverUrl"
                placeholder="图片 URL 地址"
                style="width: 400px"
              />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- Tab 2: 步骤管理（仅已保存项目可显示） -->
        <el-tab-pane
          v-if="!isNew"
          label="步骤管理"
          name="steps"
          :disabled="!editable"
        >
          <div class="mb-3">
            <el-button
              type="primary"
              :disabled="!editable"
              @click="openAddStepDialog"
            >
              添加步骤
            </el-button>
          </div>
          <el-table :data="steps" style="width: 100%" empty-text="暂无步骤">
            <el-table-column
              prop="stepNo"
              label="序号"
              width="80"
              sortable
            />
            <el-table-column
              prop="title"
              label="标题"
              min-width="160"
              show-overflow-tooltip
            />
            <el-table-column
              prop="guide"
              label="引导语"
              min-width="200"
              show-overflow-tooltip
            />
            <el-table-column
              prop="audioUrl"
              label="音频"
              width="100"
              show-overflow-tooltip
            />
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  link
                  size="small"
                  @click="openEditStepDialog(row)"
                >
                  编辑
                </el-button>
                <el-button
                  type="danger"
                  link
                  size="small"
                  @click="handleDeleteStep(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 3: 材料管理（仅已保存项目可显示） -->
        <el-tab-pane
          v-if="!isNew"
          label="材料管理"
          name="materials"
          :disabled="!editable"
        >
          <div class="mb-3">
            <el-button
              type="primary"
              :disabled="!editable"
              @click="openAddMaterialDialog"
            >
              添加材料
            </el-button>
          </div>
          <el-table
            :data="materials"
            style="width: 100%"
            empty-text="暂无材料"
          >
            <el-table-column
              prop="name"
              label="名称"
              min-width="160"
              show-overflow-tooltip
            />
            <el-table-column
              prop="qty"
              label="用量"
              width="160"
              show-overflow-tooltip
            />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="danger"
                  link
                  size="small"
                  @click="handleDeleteMaterial(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 步骤编辑弹窗 -->
    <el-dialog
      v-model="stepDialogVisible"
      :title="stepDialogMode === 'add' ? '添加步骤' : '编辑步骤'"
      width="500px"
    >
      <el-form ref="stepFormRef" :model="stepForm" :rules="{ title: textRules('步骤标题', { max: 100 }) }" label-width="80px" scroll-to-error>
        <el-form-item label="序号" required>
          <el-input-number
            v-model="stepForm.stepNo"
            :min="1"
            style="width: 120px"
          />
          <span class="ml-2 text-xs muted-inline">建议连续（1, 2, 3...）</span>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="stepForm.title"
            placeholder="步骤标题"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item label="引导语">
          <el-input
            v-model="stepForm.guide"
            type="textarea"
            :rows="4"
            placeholder="步骤的详细引导语"
          />
        </el-form-item>
        <el-form-item label="引导音频">
          <!-- AW2：上传（仅 role 1/3 可见）+ 保留手动填入已授权地址 -->
          <AudioUpload
            v-if="canUploadAudio"
            v-model="stepForm.audioUrl"
            :disabled="!editable"
            @busy="audioBusy = $event"
          />
          <el-input
            v-model="stepForm.audioUrl"
            class="mt-2"
            placeholder="或手动填入已授权的音频地址（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stepDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveStep">保存</el-button>
      </template>
    </el-dialog>

    <!-- 材料添加弹窗 -->
    <el-dialog
      v-model="materialDialogVisible"
      title="添加材料"
      width="400px"
    >
      <el-form ref="materialFormRef" :model="materialForm" :rules="{ name: textRules('材料名称', { max: 100 }) }" label-width="60px" scroll-to-error>
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="materialForm.name"
            placeholder="材料名称"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item label="用量">
          <el-input
            v-model="materialForm.qty"
            placeholder="如：1块、10根"
            maxlength="50"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="materialDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveMaterial">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.form-section-title {
  margin: 4px 0 18px;
  padding-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.form-section-title:not(:first-child) {
  margin-top: 26px;
}
</style>
