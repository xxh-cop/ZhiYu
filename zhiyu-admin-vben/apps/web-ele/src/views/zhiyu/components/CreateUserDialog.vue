<script setup lang="ts">
import { ref } from 'vue';

import { useAccessStore } from '@vben/stores';

import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';

import { apiBaseURL } from '#/api/request';
import { createUser } from '#/api/zhiyu/admin-user';
import type { UserCreateBody } from '#/api/zhiyu/admin-user';
import ZyFormField from '#/components/zy-form-field/ZyFormField.vue';
import {
  nicknameRules,
  passwordRules,
  usernameRules,
} from '#/utils/form-rules';
import { focusFirstError } from '#/utils/form-a11y';

/** 新建用户弹窗：表单、校验、头像上传与提交均内聚于此，成功后emit created 由页面刷新列表 */
const visible = defineModel<boolean>({ default: false });
const emit = defineEmits<{ created: [] }>();

const createFormRef = ref<FormInstance>();
const creating = ref(false);
const avatarUploading = ref(false);
const avatarInput = ref<HTMLInputElement | null>(null);
const accessStore = useAccessStore();

const emptyForm: UserCreateBody = {
  username: '',
  password: '',
  nickname: '',
  role: 0,
  school: '',
  intro: '',
  avatarUrl: '',
  skill: '',
  honor: '',
};

const createForm = ref<UserCreateBody>({ ...emptyForm });

/** 规则组与传承人新建弹窗共用，文案与后端约束一处维护 */
const createRules: FormRules = {
  username: usernameRules(),
  password: passwordRules(),
  nickname: nicknameRules(),
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
};

function open() {
  createForm.value = { ...emptyForm };
  visible.value = true;
}

function handleRoleChange() {
  // 切换角色时清空传承人专用字段
  if (createForm.value.role !== 1) {
    createForm.value.skill = '';
    createForm.value.honor = '';
  }
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
    const response = await fetch(`${apiBaseURL}/files/upload/image`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: formData,
    });
    const result = await response.json();
    if (result.code === 0 && result.data?.url) {
      createForm.value.avatarUrl = result.data.url;
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

async function submitCreate() {
  if (!createFormRef.value) return;
  const valid = await createFormRef.value.validate().catch(() => false);
  if (!valid) {
    await focusFirstError();
    return;
  }

  creating.value = true;
  try {
    const body: UserCreateBody = {
      username: createForm.value.username,
      password: createForm.value.password,
      nickname: createForm.value.nickname,
      role: createForm.value.role,
    };
    if (createForm.value.school) body.school = createForm.value.school;
    if (createForm.value.intro) body.intro = createForm.value.intro;
    if (createForm.value.avatarUrl) body.avatarUrl = createForm.value.avatarUrl;
    if (createForm.value.role === 1) {
      if (createForm.value.skill) body.skill = createForm.value.skill;
      if (createForm.value.honor) body.honor = createForm.value.honor;
    }

    await createUser(body);
    ElMessage.success('用户创建成功');
    visible.value = false;
    emit('created');
  } catch (err: any) {
    const code = err?.response?.data?.code;
    const msg = err?.response?.data?.message || '创建失败';
    if (code === 2002) {
      ElMessage.error('登录名已存在');
    } else {
      ElMessage.error(msg);
    }
  } finally {
    creating.value = false;
  }
}

defineExpose({ open });
</script>

<template>
  <el-dialog v-model="visible" title="新建用户" width="600px">
    <el-form
      ref="createFormRef"
      :model="createForm"
      :rules="createRules"
      label-width="100px"
      scroll-to-error
    >
      <!-- 头像上传 -->
      <el-form-item label="头像">
        <div class="flex items-center gap-3">
          <div class="create-avatar-wrapper">
            <img
              v-if="createForm.avatarUrl"
              :src="getAvatarUrl(createForm.avatarUrl)"
              alt="头像"
              class="create-avatar-img"
            />
            <div v-else class="create-avatar-placeholder">
              {{ createForm.nickname.charAt(0) || '?' }}
            </div>
          </div>
          <el-button :loading="avatarUploading" @click="triggerAvatarUpload">
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

      <ZyFormField label="登录名" prop="username">
        <el-input
          v-model="createForm.username"
          placeholder="3-50 位（用于登录）"
          style="max-width: 300px"
        />
      </ZyFormField>

      <ZyFormField label="密码" prop="password">
        <el-input
          v-model="createForm.password"
          type="password"
          placeholder="6-32 位"
          show-password
          style="max-width: 300px"
        />
      </ZyFormField>

      <ZyFormField label="显示昵称" prop="nickname">
        <el-input
          v-model="createForm.nickname"
          placeholder="不超过 50 字（用于展示）"
          style="max-width: 300px"
        />
      </ZyFormField>

      <ZyFormField label="角色" prop="role">
        <el-select
          v-model="createForm.role"
          placeholder="请选择角色"
          style="width: 200px"
          @change="handleRoleChange"
        >
          <el-option label="学生" :value="0" />
          <el-option label="传承人" :value="1" />
          <el-option label="咨询师" :value="2" />
          <el-option label="管理员" :value="3" />
        </el-select>
      </ZyFormField>

      <ZyFormField label="学校">
        <el-input
          v-model="createForm.school"
          placeholder="可选"
          style="max-width: 300px"
        />
      </ZyFormField>

      <ZyFormField label="个人简介">
        <el-input
          v-model="createForm.intro"
          type="textarea"
          :rows="3"
          placeholder="可选"
        />
      </ZyFormField>

      <!-- 传承人专用字段 -->
      <template v-if="createForm.role === 1">
        <el-divider content-position="left">传承人资料</el-divider>
        <ZyFormField label="擅长技艺">
          <el-input
            v-model="createForm.skill"
            placeholder="如：赣绣、陶瓷等"
            style="max-width: 300px"
          />
        </ZyFormField>
        <ZyFormField label="荣誉">
          <el-input
            v-model="createForm.honor"
            placeholder="如：非遗传承人等"
            style="max-width: 300px"
          />
        </ZyFormField>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="creating" @click="submitCreate">
        创建
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.create-avatar-wrapper {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  border: 2px solid var(--el-border-color);
}

.create-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.create-avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: bold;
  color: var(--el-color-white);
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-light-3) 100%);
}
</style>
