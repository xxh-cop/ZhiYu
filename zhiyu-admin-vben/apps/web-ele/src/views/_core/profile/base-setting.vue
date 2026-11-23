<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { useAccessStore, useUserStore } from '@vben/stores';

import { ElMessage } from 'element-plus';
import {
  ElButton,
  ElCard,
  ElInput,
  ElTag,
} from 'element-plus';

import { getUserInfoApi, updateUserProfile } from '#/api';
import { apiBaseURL } from '#/api/request';

const userStore = useUserStore();
const accessStore = useAccessStore();

// ── 表单数据 ──
const form = ref({
  nickname: '',
  username: '',
  role: 0,
  roleLabel: '',
  school: '',
  intro: '',
  avatarUrl: '',
});

const saving = ref(false);
const uploading = ref(false);
const avatarInput = ref<HTMLInputElement | null>(null);

// 角色码 → 中文标签
const roleLabels: Record<number, string> = {
  0: '学生',
  1: '传承人',
  2: '咨询师',
  3: '管理员',
};

// ── 加载用户信息 ──
onMounted(async () => {
  try {
    const data = await getUserInfoApi();
    form.value.nickname = data.realName || '';
    form.value.username = data.username || '';
    form.value.role = (data as any).role ?? 0;
    form.value.roleLabel = roleLabels[form.value.role] || '未知';
    form.value.school = (data as any).school || '';
    form.value.intro = (data as any).intro || '';
    form.value.avatarUrl = (data as any).avatarUrl || '';
  } catch {
    ElMessage.error('加载用户信息失败');
  }
});

// ── 头像上传 ──
function triggerFileSelect() {
  avatarInput.value?.click();
}

async function handleAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;

  // 校验文件类型
  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
  if (!allowedTypes.includes(file.type)) {
    ElMessage.error('仅支持 JPG / PNG / GIF / WebP 格式');
    input.value = '';
    return;
  }

  // 校验文件大小（5MB）
  const maxSize = 5 * 1024 * 1024;
  if (file.size > maxSize) {
    ElMessage.error('图片大小不能超过 5MB');
    input.value = '';
    return;
  }

  uploading.value = true;
  try {
    const formData = new FormData();
    formData.append('file', file);

    // 获取 token
    const token = accessStore.accessToken;
    const response = await fetch(`${apiBaseURL}/files/upload/image`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
      },
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
    ElMessage.error('头像上传失败，请检查网络');
  } finally {
    uploading.value = false;
    input.value = '';
  }
}

// ── 头像 URL 拼接（处理相对路径）──
// 后端返回的相对 URL 形如 /uploads/images/yyyy/MM/dd/xxx.jpg，
// 后端已配置 /uploads/** → file:./uploads/ 静态映射（免鉴权），
// 因此直接以相对路径请求即可（dev 下经 Vite /uploads proxy 转发），
// 切勿再拼 /api 前缀（后端无 /api/uploads/** 映射，会 404）。
function getAvatarUrl(url: string): string {
  if (!url) return '';
  if (url.startsWith('http')) return url;
  return url;
}

// ── 保存 ──
async function handleSave() {
  if (!form.value.nickname.trim()) {
    ElMessage.warning('昵称不能为空');
    return;
  }

  saving.value = true;
  try {
    await updateUserProfile({
      nickname: form.value.nickname,
      avatarUrl: form.value.avatarUrl || undefined,
      school: form.value.school || undefined,
      intro: form.value.intro || undefined,
    });

    ElMessage.success('保存成功');

    // 刷新 authStore 的用户信息，让顶部导航头像/昵称同步
    const userInfo = await getUserInfoApi();
    userStore.setUserInfo(userInfo);
  } catch (err: any) {
    const msg = err?.response?.data?.message || '保存失败';
    ElMessage.error(msg);
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="p-4">
    <ElCard>
      <template #header>
        <span class="text-lg font-medium">基本设置</span>
      </template>

      <!-- 头像区域 -->
      <div class="mb-6 flex items-center gap-4">
        <div class="avatar-wrapper">
          <img
            v-if="form.avatarUrl"
            :src="getAvatarUrl(form.avatarUrl)"
            alt="头像"
            class="avatar-img"
          />
          <div v-else class="avatar-placeholder">
            {{ form.nickname.charAt(0) || '?' }}
          </div>
        </div>
        <div>
          <ElButton type="primary" :loading="uploading" @click="triggerFileSelect">
            更换头像
          </ElButton>
          <p class="mt-1 text-xs text-gray-400">
            支持 JPG / PNG / GIF / WebP，最大 5MB
          </p>
        </div>
        <input
          ref="avatarInput"
          type="file"
          accept="image/jpeg,image/png,image/gif,image/webp"
          style="display: none"
          @change="handleAvatarChange"
        />
      </div>

      <!-- 表单区域 -->
      <div class="form-area">
        <div class="form-item">
          <label class="form-label">昵称</label>
          <ElInput
            v-model="form.nickname"
            placeholder="请输入昵称"
            :maxlength="50"
            show-word-limit
            style="max-width: 360px"
          />
        </div>

        <div class="form-item">
          <label class="form-label">用户名</label>
          <ElInput
            :model-value="form.username"
            disabled
            placeholder="用户名"
            style="max-width: 360px"
          />
        </div>

        <div class="form-item">
          <label class="form-label">角色</label>
          <div class="form-value">
            <ElTag :type="form.role === 3 ? 'danger' : form.role === 1 ? 'warning' : form.role === 2 ? 'success' : 'info'">
              {{ form.roleLabel }}
            </ElTag>
          </div>
        </div>

        <div class="form-item">
          <label class="form-label">学校</label>
          <ElInput
            v-model="form.school"
            placeholder="请输入学校名称"
            :maxlength="100"
            style="max-width: 360px"
          />
        </div>

        <div class="form-item">
          <label class="form-label">个人简介</label>
          <ElInput
            v-model="form.intro"
            type="textarea"
            :rows="3"
            placeholder="介绍一下自己吧"
            :maxlength="500"
            show-word-limit
            style="max-width: 480px"
          />
        </div>
      </div>

      <!-- 保存按钮 -->
      <div class="mt-6">
        <ElButton type="primary" :loading="saving" @click="handleSave">
          保存
        </ElButton>
      </div>
    </ElCard>
  </div>
</template>

<style scoped>
.avatar-wrapper {
  width: 120px;
  height: 120px;
  border-radius: 50%;
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
  font-size: 42px;
  font-weight: bold;
  color: var(--el-color-white);
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-light-3) 100%);
}

.form-area {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.form-label {
  width: 80px;
  text-align: right;
  line-height: 32px;
  color: var(--el-text-color-regular);
  font-size: 14px;
  flex-shrink: 0;
}

.form-value {
  line-height: 32px;
}
</style>
