<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { forgotPasswordApi, resetPasswordApi } from '#/api/core/auth';

defineOptions({ name: 'ForgotPassword' });

/**
 * 忘记密码（AL1）：申请重置令牌 → 令牌 + 新密码重置，两步独立页。
 * 选独立页而非内嵌登录页：两步表单较长，内嵌会挤压登录主流程；
 * 复用 AuthPageLayout 品牌外壳，视觉与登录一致。
 *
 * 后端语义对接（不自造状态）：
 * - POST /api/auth/password/forgot：账号不存在也返回成功（不泄露存在性），
 *   发送通道未配置返回 3001「忘记密码功能暂未开放」→ 明确展示，不假装成功；
 * - POST /api/auth/password/reset：令牌无效/过期返回 3001「重置令牌无效或已过期」；
 * - 后端未定义重发节流，本页不做倒计时。
 */
const router = useRouter();

const step = ref<1 | 2>(1);
const loading = ref(false);
const error = ref('');
const unavailable = ref(false);
const applied = ref(false);
const resetDone = ref(false);

const usernameInput = ref<HTMLInputElement | null>(null);
const tokenInput = ref<HTMLInputElement | null>(null);

const form = reactive({ username: '', token: '', newPassword: '', confirm: '' });

function stagger(index: number) {
  return { '--stagger-i': String(index) };
}

/**
 * 业务失败（code !== 0）时请求层抛的是携带 response 的 plain object 而非 Error，
 * 后端 message 在 e.response.data.message；网络层 Error 才有 e.message。
 */
function errMsg(e: any): string {
  return e?.response?.data?.message || e?.message || '';
}

/** 第一步：申请重置令牌 */
async function apply() {
  if (loading.value) return;
  error.value = '';
  const username = form.username.trim();
  if (!username) {
    error.value = '请填写工作账号';
    usernameInput.value?.focus();
    return;
  }
  loading.value = true;
  try {
    await forgotPasswordApi(username);
    // 不泄露账号是否存在：与后端受理口径一致的中性提示
    applied.value = true;
    step.value = 2;
    setTimeout(() => tokenInput.value?.focus(), 0);
  } catch (e: any) {
    const msg = errMsg(e);
    if (msg.includes('暂未开放')) {
      unavailable.value = true;
    } else {
      error.value = msg || '申请未完成，请检查网络后重试。';
    }
  } finally {
    loading.value = false;
  }
}

/** 第二步：令牌 + 新密码重置 */
async function reset() {
  if (loading.value) return;
  error.value = '';
  if (!form.token.trim()) {
    error.value = '请填写重置令牌';
    tokenInput.value?.focus();
    return;
  }
  if (form.newPassword.length < 6 || form.newPassword.length > 32) {
    error.value = '新密码长度需在 6-32 位之间';
    return;
  }
  if (form.newPassword !== form.confirm) {
    error.value = '两次输入的新密码不一致';
    return;
  }
  loading.value = true;
  try {
    await resetPasswordApi(form.token.trim(), form.newPassword);
    resetDone.value = true;
  } catch (e: any) {
    // 3001「重置令牌无效或已过期」等服务端原文直接展示
    error.value = errMsg(e) || '重置未完成，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

function backToLogin() {
  router.push('/auth/login');
}
</script>

<template>
  <div class="login-form">
    <p class="login-kicker zy-slide-up zy-stagger" :style="stagger(0)">PASSWORD RECOVERY</p>
    <h2 class="zy-slide-up zy-stagger" :style="stagger(1)">找回登录密码</h2>
    <p class="login-desc zy-slide-up zy-stagger" :style="stagger(2)">
      两步完成：先申请一次性重置令牌，再设置新密码。
    </p>

    <!-- 发送通道未配置：明确展示真实状态，不假装发送成功 -->
    <div v-if="unavailable" class="zy-slide-up zy-stagger" :style="stagger(3)">
      <p class="notice" role="alert">
        忘记密码功能暂未开通。请联系平台管理员人工重置，或由学校统一开通发送通道后再试。
      </p>
      <button class="login-submit" type="button" @click="backToLogin">
        <span class="submit-label">返回登录</span>
        <span aria-hidden="true" class="submit-arrow">→</span>
      </button>
    </div>

    <!-- 重置成功 -->
    <div v-else-if="resetDone" class="zy-slide-up zy-stagger" :style="stagger(3)">
      <p class="notice notice--ok" role="status">密码已重置，请使用新密码登录。</p>
      <button class="login-submit" type="button" @click="backToLogin">
        <span class="submit-label">去登录</span>
        <span aria-hidden="true" class="submit-arrow">→</span>
      </button>
    </div>

    <template v-else>
      <!-- 第一步：申请令牌 -->
      <form
        v-if="step === 1"
        class="zy-slide-up zy-stagger"
        :style="stagger(3)"
        @submit.prevent="apply"
      >
        <div class="zy-field">
          <label class="zy-field-label" for="fp-username">
            工作账号<span aria-hidden="true" class="req">*</span>
          </label>
          <span :class="{ 'is-disabled': loading }" class="zy-input-shell">
            <input
              id="fp-username"
              ref="usernameInput"
              v-model="form.username"
              :aria-describedby="error ? 'fp-error' : undefined"
              autocapitalize="none"
              :spellcheck="false"
              maxlength="50"
              name="username"
              autocomplete="username"
              placeholder="请输入账号"
              :disabled="loading"
            />
          </span>
          <p class="zy-field-hint">
            若账号存在，重置指引将发送至其绑定通道（30 分钟内有效）；不会提示账号是否存在。
          </p>
        </div>

        <p v-if="error" id="fp-error" class="login-error" role="alert">{{ error }}</p>

        <button :class="{ 'is-busy': loading }" class="login-submit" type="submit" :disabled="loading">
          <span class="submit-label">{{ loading ? '正在申请…' : '申请重置令牌' }}</span>
          <span aria-hidden="true" class="submit-arrow">→</span>
        </button>
      </form>

      <!-- 第二步：令牌 + 新密码 -->
      <form v-else class="zy-slide-up zy-stagger" :style="stagger(3)" @submit.prevent="reset">
        <p v-if="applied" class="notice notice--ok" role="status">
          若该账号存在，重置指引已发出（30 分钟内有效）。请查收后填写令牌。
        </p>
        <div class="zy-field">
          <label class="zy-field-label" for="fp-token">
            重置令牌<span aria-hidden="true" class="req">*</span>
          </label>
          <span :class="{ 'is-disabled': loading }" class="zy-input-shell">
            <input
              id="fp-token"
              ref="tokenInput"
              v-model="form.token"
              maxlength="128"
              name="token"
              autocomplete="one-time-code"
              placeholder="粘贴收到的重置令牌"
              :disabled="loading"
            />
          </span>
        </div>

        <div class="zy-field zy-field--gap">
          <label class="zy-field-label" for="fp-new">
            新密码<span aria-hidden="true" class="req">*</span>
          </label>
          <span :class="{ 'is-disabled': loading }" class="zy-input-shell">
            <input
              id="fp-new"
              v-model="form.newPassword"
              type="password"
              maxlength="32"
              name="new-password"
              autocomplete="new-password"
              placeholder="6-32 位"
              :disabled="loading"
            />
          </span>
        </div>

        <div class="zy-field zy-field--gap">
          <label class="zy-field-label" for="fp-confirm">
            确认新密码<span aria-hidden="true" class="req">*</span>
          </label>
          <span :class="{ 'is-disabled': loading }" class="zy-input-shell">
            <input
              id="fp-confirm"
              v-model="form.confirm"
              type="password"
              maxlength="32"
              autocomplete="new-password"
              placeholder="再次输入新密码"
              :disabled="loading"
            />
          </span>
        </div>

        <p v-if="error" id="fp-error" class="login-error" role="alert">{{ error }}</p>

        <button :class="{ 'is-busy': loading }" class="login-submit" type="submit" :disabled="loading">
          <span class="submit-label">{{ loading ? '正在重置…' : '重置密码' }}</span>
          <span aria-hidden="true" class="submit-arrow">→</span>
        </button>
        <button class="link-back" type="button" @click="step = 1">返回重新申请</button>
      </form>

      <div class="login-divider zy-slide-up zy-stagger" :style="stagger(4)"></div>
      <p class="login-help zy-slide-up zy-stagger" :style="stagger(4)">
        想起来了？<button class="link-back" type="button" @click="backToLogin">返回登录</button>
      </p>
    </template>
  </div>
</template>

<style scoped>
/* 视觉与登录页同套令牌（zy-field / 提交按钮 / 提示动效），保持品牌一致 */
.login-form {
  color: var(--el-text-color-primary);
}
.login-kicker {
  margin-bottom: 18px;
  font-size: 10px;
  letter-spacing: 3px;
  color: var(--el-text-color-regular);
}
.login-form h2 {
  font-size: 30px;
  font-weight: 550;
  letter-spacing: 1px;
}
.login-desc {
  margin-top: 13px;
  margin-bottom: 30px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.zy-field--gap {
  margin-top: 22px;
}

.notice {
  margin: 0 0 18px;
  padding: 12px 14px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-7);
  border-radius: 10px;
}
.notice--ok {
  color: var(--el-color-success-dark-2);
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-7);
}

.login-submit {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: 52px;
  margin-top: 28px;
  padding: 0 20px;
  overflow: hidden;
  font-size: 13px;
  color: var(--el-color-white);
  background: linear-gradient(
    115deg,
    color-mix(in oklab, var(--el-color-primary) 84%, black) 0%,
    var(--el-color-primary) 46%,
    color-mix(in oklab, var(--el-color-primary) 78%, white) 100%
  );
  border: 0;
  border-radius: var(--zy-pearl-radius);
  box-shadow:
    0 10px 24px -14px var(--zy-accent-glow),
    inset 0 1px 0 rgb(255 255 255 / 22%);
}
.login-submit:disabled {
  opacity: 0.6;
}
.login-submit.is-busy {
  animation: zyBusy 1.3s var(--zy-ease-in-out) infinite alternate;
}
.submit-arrow {
  transition: transform var(--zy-duration-base) var(--zy-ease-smooth);
}
.login-submit:hover .submit-arrow {
  transform: translateX(5px);
}

@keyframes zyBusy {
  from {
    opacity: 0.55;
  }
  to {
    opacity: 0.85;
  }
}

.link-back {
  margin-top: 14px;
  padding: 0;
  font-size: 12px;
  color: var(--el-color-primary);
  background: transparent;
  border: 0;
  cursor: pointer;
}

.login-divider {
  height: 1px;
  margin: 28px 0 18px;
  background: linear-gradient(90deg, transparent, var(--el-border-color), transparent);
}
.login-help {
  font-size: 11px;
  line-height: 2;
  color: var(--el-text-color-regular);
}
.login-error {
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-color-danger);
  animation: zyNudge 0.26s var(--zy-ease-out);
}

@keyframes zyNudge {
  0% {
    opacity: 0;
    transform: translateX(-6px);
  }
  60% {
    transform: translateX(3px);
  }
  100% {
    opacity: 1;
    transform: translateX(0);
  }
}

button:focus-visible {
  outline: 3px solid var(--el-color-primary);
  outline-offset: 3px;
}

@media (prefers-reduced-motion: reduce) {
  .login-submit,
  .submit-arrow {
    transition: none;
  }
  .login-error,
  .login-submit.is-busy {
    animation: none;
  }
}
</style>
