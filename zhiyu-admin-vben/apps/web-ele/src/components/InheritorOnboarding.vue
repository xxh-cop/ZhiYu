<script setup lang="ts">
import { ref, watch } from 'vue';

import { useAccessStore } from '@vben/stores';

import { ElMessage } from 'element-plus';

import { updateMyInheritorProfile } from '#/api/zhiyu/inheritor';

interface Props {
  visible: boolean;
}

interface Emits {
  (e: 'update:visible', val: boolean): void;
  (e: 'submit'): void;
  (e: 'skip'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

const accessStore = useAccessStore();

const form = ref({
  avatarUrl: '',
  skill: '',
  intro: '',
  honor: '',
});

const saving = ref(false);
const avatarUploading = ref(false);
const avatarInput = ref<HTMLInputElement | null>(null);

const dialogVisible = ref(props.visible);

watch(
  () => props.visible,
  (val) => {
    dialogVisible.value = val;
  },
);

watch(dialogVisible, (val) => {
  emit('update:visible', val);
});

function handleClose() {
  dialogVisible.value = false;
}

function handleSkip() {
  localStorage.setItem('profileGuideDismissed', 'true');
  emit('skip');
  handleClose();
}

function triggerAvatarUpload() {
  avatarInput.value?.click();
}

async function handleAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;

  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
  if (!allowedTypes.includes(file.type)) {
    ElMessage.error('仅支持 JPG / PNG / GIF / WebP 格式');
    input.value = '';
    return;
  }

  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB');
    input.value = '';
    return;
  }

  avatarUploading.value = true;
  try {
    const formData = new FormData();
    formData.append('file', file);
    const token = accessStore.accessToken;
    const response = await fetch('/api/files/upload/image', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: formData,
    });
    const result = await response.json();
    if (result.code === 0 && result.data?.url) {
      form.value.avatarUrl = result.data.url;
      ElMessage.success('头像上传成功');
    } else {
      ElMessage.error(result.message || '头像上传失败');
    }
  } catch {
    ElMessage.error('头像上传失败');
  } finally {
    avatarUploading.value = false;
    input.value = '';
  }
}

function getAvatarUrl(url: string): string {
  if (!url) return '';
  if (url.startsWith('http')) return url;
  return `/api${url}`;
}

async function handleSave() {
  if (!form.value.skill.trim()) {
    ElMessage.warning('请输入擅长技艺');
    return;
  }
  if (!form.value.intro.trim()) {
    ElMessage.warning('请输入个人简介');
    return;
  }

  saving.value = true;
  try {
    const body: Record<string, string> = {
      skill: form.value.skill.trim(),
      intro: form.value.intro.trim(),
    };
    if (form.value.honor.trim()) body.honor = form.value.honor.trim();
    if (form.value.avatarUrl) body.avatarUrl = form.value.avatarUrl;

    await updateMyInheritorProfile(body);
    localStorage.setItem('profileGuideDismissed', 'true');
    ElMessage.success('资料已保存');
    emit('submit');
    handleClose();
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="完善传承人资料"
    width="560px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
  >
    <template #header>
      <div class="onboarding-header">
        <h3 class="onboarding-title">完善传承人资料</h3>
        <p class="onboarding-subtitle">
          完善的资料有助于学生了解你和你的技艺
        </p>
      </div>
    </template>

    <el-form label-width="90px" class="onboarding-form">
      <!-- 头像 -->
      <el-form-item label="头像">
        <div class="flex items-center gap-3">
          <div class="avatar-wrapper">
            <img
              v-if="form.avatarUrl"
              :src="getAvatarUrl(form.avatarUrl)"
              alt="头像"
              class="avatar-img"
            />
            <div v-else class="avatar-placeholder">
              <span class="avatar-plus">+</span>
            </div>
          </div>
          <el-button
            :loading="avatarUploading"
            size="small"
            @click="triggerAvatarUpload"
          >
            上传头像
          </el-button>
        </div>
        <input
          ref="avatarInput"
          type="file"
          accept="image/jpeg,image/png,image/gif,image/webp"
          style="display: none"
          @change="handleAvatarChange"
        />
      </el-form-item>

      <!-- 擅长技艺 -->
      <el-form-item label="擅长技艺" required>
        <el-input
          v-model="form.skill"
          placeholder="如：赣绣、陶瓷、扎染等"
          maxlength="100"
          show-word-limit
        />
      </el-form-item>

      <!-- 个人简介 -->
      <el-form-item label="个人简介" required>
        <el-input
          v-model="form.intro"
          type="textarea"
          :rows="3"
          placeholder="介绍你的从艺经历与教学风格"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <!-- 荣誉 -->
      <el-form-item label="荣誉">
        <el-input
          v-model="form.honor"
          placeholder="如：省级非遗传承人等（可选）"
          maxlength="255"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleSkip">稍后完善</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.onboarding-header {
  text-align: center;
  margin-bottom: 8px;
}

.onboarding-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0 0 4px;
}

.onboarding-subtitle {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0;
}

.onboarding-form {
  padding: 8px 4px;
}

.avatar-wrapper {
  width: 64px;
  height: 64px;
  border-radius: 9999px;
  overflow: hidden;
  flex-shrink: 0;
  border: 2px solid var(--el-border-color);
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.avatar-plus {
  font-size: 24px;
  font-weight: 300;
  line-height: 1;
}
</style>
