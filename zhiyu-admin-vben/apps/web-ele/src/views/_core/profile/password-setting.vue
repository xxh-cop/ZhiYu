<script setup lang="ts">
import { reactive, ref } from 'vue';

import { ElButton, ElCard, ElInput, ElForm, ElFormItem, ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';

import { changePassword } from '#/api';

const formRef = ref<FormInstance>();
const saving = ref(false);

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
});

const rules: FormRules = {
  oldPassword: [
    { required: true, message: '请输入旧密码', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需在 6-32 之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的密码不一致'));
        } else if (value === form.oldPassword) {
          callback(new Error('新密码不能与旧密码相同'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
};

async function handleSave() {
  if (!formRef.value) return;

  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  saving.value = true;
  try {
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    });

    ElMessage.success('密码修改成功，下次登录请使用新密码');
    formRef.value.resetFields();
  } catch (err: any) {
    const code = err?.response?.data?.code;
    const msg = err?.response?.data?.message || '密码修改失败';

    if (code === 2001) {
      // 旧密码错误 — 在旧密码输入框上标红
      formRef.value?.validateField('oldPassword');
      ElMessage.error(msg);
    } else {
      ElMessage.error(msg);
    }
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="p-4">
    <ElCard>
      <template #header>
        <span class="text-lg font-medium">修改密码</span>
      </template>

      <ElForm
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        style="max-width: 480px"
      >
        <ElFormItem label="旧密码" prop="oldPassword">
          <ElInput
            v-model="form.oldPassword"
            type="password"
            placeholder="请输入旧密码"
            show-password
          />
        </ElFormItem>

        <ElFormItem label="新密码" prop="newPassword">
          <ElInput
            v-model="form.newPassword"
            type="password"
            placeholder="请输入新密码（6-32位）"
            show-password
          />
        </ElFormItem>

        <ElFormItem label="确认密码" prop="confirmPassword">
          <ElInput
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </ElFormItem>

        <ElFormItem>
          <ElButton type="primary" :loading="saving" @click="handleSave">
            修改密码
          </ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>
  </div>
</template>
