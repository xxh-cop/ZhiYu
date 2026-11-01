<script setup lang="ts">
import { reactive, ref } from 'vue';
import { NO_ADMIN_ROLE_ERROR } from '#/router/role-access';
import { useAuthStore } from '#/store';
defineOptions({ name: 'Login' });
const authStore = useAuthStore();
const form = reactive({ username: '', password: '' });
const visible = ref(false), error = ref(''), caps = ref(false);
const usernameInput = ref<HTMLInputElement | null>(null);
const passwordInput = ref<HTMLInputElement | null>(null);
const invalid = reactive({ username: false, password: false });

function stagger(index: number) {
  return { '--stagger-i': String(index) };
}

/** 清空后回到该字段，避免焦点丢到 body */
function clearField(target: 'password' | 'username') {
  form[target] = '';
  invalid[target] = false;
  (target === 'username' ? usernameInput : passwordInput).value?.focus();
}

async function submit() {
  if (authStore.loginLoading) return;
  error.value = '';
  invalid.username = !form.username.trim();
  invalid.password = !form.password;
  if (invalid.username || invalid.password) {
    error.value = invalid.username ? '请填写工作账号' : '请填写登录密码';
    (invalid.username ? usernameInput : passwordInput).value?.focus();
    return;
  }
  try {
    await authStore.authLogin({ username: form.username.trim(), password: form.password });
  } catch (err) {
    error.value =
      err instanceof Error && err.message === NO_ADMIN_ROLE_ERROR
        ? '该账号没有管理端访问权限。学生账号请使用学生端；如需协助请联系平台管理员。'
        : '登录未完成。请确认工作账号和密码，或检查网络后重试。学生账号请使用学生端。';
  }
}
</script>
<template>
  <div class="login-form">
    <img class="mobile-logo" src="/logo.svg" alt="织愈" />
    <p class="login-kicker zy-slide-up zy-stagger" :style="stagger(0)">WELCOME BACK</p>
    <h2 class="zy-slide-up zy-stagger" :style="stagger(1)">欢迎回到织愈</h2>
    <p class="login-desc zy-slide-up zy-stagger" :style="stagger(2)">
      登录工作空间，开始今天的创作与协作。
    </p>

    <form class="zy-slide-up zy-stagger" :style="stagger(3)" @submit.prevent="submit">
      <div class="zy-field">
        <label class="zy-field-label" for="zy-user">
          工作账号<span aria-hidden="true" class="req">*</span>
        </label>
        <span
          :class="{ 'is-disabled': authStore.loginLoading, 'is-invalid': invalid.username }"
          class="zy-input-shell"
        >
          <span aria-hidden="true" class="zy-input-lead">
            <svg
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="1.8"
              viewBox="0 0 24 24"
            >
              <circle cx="12" cy="8" r="3.6" />
              <path d="M5 20a7 7 0 0 1 14 0" />
            </svg>
          </span>
          <input
            id="zy-user"
            ref="usernameInput"
            :aria-invalid="invalid.username"
            :aria-describedby="error ? 'login-error' : undefined"
            autocapitalize="none"
            :spellcheck="false"
            maxlength="50"
            v-model="form.username"
            name="username"
            autocomplete="username"
            placeholder="请输入账号"
            :disabled="authStore.loginLoading"
          />
          <button
            v-if="form.username && !authStore.loginLoading"
            aria-label="清空工作账号"
            class="zy-input-action is-clear"
            type="button"
            @click="clearField('username')"
          >
            <svg
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              viewBox="0 0 24 24"
              stroke-width="2"
            >
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
          </button>
        </span>
        <p class="zy-field-hint">使用平台分配的工作账号，学生账号请走学生端。</p>
      </div>

      <div class="zy-field zy-field--gap">
        <label class="zy-field-label" for="zy-password">
          登录密码<span aria-hidden="true" class="req">*</span>
        </label>
        <span
          :class="{ 'is-disabled': authStore.loginLoading, 'is-invalid': invalid.password }"
          class="zy-input-shell"
        >
          <span aria-hidden="true" class="zy-input-lead">
            <svg
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="1.8"
              viewBox="0 0 24 24"
            >
              <rect height="10" rx="2.2" width="16" x="4" y="10.5" />
              <path d="M8 10.5V7.6a4 4 0 0 1 8 0v2.9" />
            </svg>
          </span>
          <input
            id="zy-password"
            ref="passwordInput"
            :aria-invalid="invalid.password"
            :aria-describedby="error ? 'login-error' : undefined"
            maxlength="32"
            v-model="form.password"
            name="password"
            :type="visible ? 'text' : 'password'"
            autocomplete="current-password"
            placeholder="请输入密码"
            :disabled="authStore.loginLoading"
            @keyup="caps = $event.getModifierState('CapsLock')"
            @blur="caps = false"
          />
          <button
            v-if="form.password && !authStore.loginLoading"
            aria-label="清空登录密码"
            class="zy-input-action is-clear"
            type="button"
            @click="clearField('password')"
          >
            <svg
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              viewBox="0 0 24 24"
              stroke-width="2"
            >
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
          </button>
          <button
            :aria-label="visible ? '隐藏密码' : '显示密码'"
            :aria-pressed="visible"
            :disabled="authStore.loginLoading"
            class="zy-input-action"
            type="button"
            @click="visible = !visible"
          >
            <svg
              v-if="visible"
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="1.8"
              viewBox="0 0 24 24"
            >
              <path d="M10.6 5.2A9.7 9.7 0 0 1 12 5.1c6.2 0 9.5 6.9 9.5 6.9a17.6 17.6 0 0 1-2.4 3.4" />
              <path d="M6.7 6.7A17.3 17.3 0 0 0 2.5 12c0 .1 3.3 6.9 9.5 6.9a9.6 9.6 0 0 0 5.2-1.5" />
              <path d="m3.5 3.5 17 17" />
              <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
            </svg>
            <svg
              v-else
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="1.8"
              viewBox="0 0 24 24"
            >
              <path d="M2.5 12s3.3-6.9 9.5-6.9S21.5 12 21.5 12s-3.3 6.9-9.5 6.9S2.5 12 2.5 12Z" />
              <circle cx="12" cy="12" r="2.9" />
            </svg>
          </button>
        </span>
        <p class="zy-field-hint">忘记密码请联系平台管理员重置。</p>
      </div>

      <p v-if="caps" class="caps">大写锁定已开启</p>
      <p v-if="error" id="login-error" class="login-error" role="alert">{{ error }}</p>

      <button
        :class="{ 'is-busy': authStore.loginLoading }"
        class="login-submit"
        type="submit"
        :disabled="authStore.loginLoading"
      >
        <span class="submit-label">
          {{ authStore.loginLoading ? '正在登录…' : '登录工作空间' }}
        </span>
        <span aria-hidden="true" class="submit-arrow">→</span>
      </button>
    </form>

    <div class="login-divider zy-slide-up zy-stagger" :style="stagger(4)"></div>
    <p class="login-help zy-slide-up zy-stagger" :style="stagger(4)">
      供管理员、传承人与咨询师使用。<br />账号问题请联系平台管理员。
    </p>
    <div class="login-tags zy-slide-up zy-stagger" :style="stagger(5)">
      <span>内容管理</span>
      <span>活动协作</span>
      <span>授权访问</span>
    </div>
  </div>
</template>

<style scoped>
.login-form {
  color: var(--el-text-color-primary);
}
.mobile-logo {
  width: 52px;
  margin-bottom: 26px;
  display: none;
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

/* 主按钮：主色渐变 + 悬停高光扫过，与左侧故事区的掠光同一条光线语言 */
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
  transition:
    box-shadow var(--zy-duration-base) var(--zy-ease-out),
    transform var(--zy-duration-fast) var(--zy-ease-out);
}
.login-submit::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(105deg, transparent 42%, rgb(255 255 255 / 34%) 50%, transparent 58%);
  transform: translateX(-120%);
  transition: transform 0.75s var(--zy-ease-smooth);
  pointer-events: none;
}
.login-submit:hover::after {
  transform: translateX(120%);
}
.login-submit:hover {
  box-shadow:
    0 16px 32px -14px var(--zy-accent-glow),
    inset 0 1px 0 rgb(255 255 255 / 28%);
}
.login-submit:active {
  transform: scale(0.99);
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
.login-tags {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 18px;
  font-size: 10px;
  color: var(--el-text-color-regular);
}
.login-tags span {
  padding: 6px 11px;
  background: var(--zy-glass-chip);
  border: 1px solid var(--zy-glass-border);
  border-radius: 999px;
  transition: color var(--zy-duration-fast) var(--zy-ease-out);
}
.login-tags span:hover {
  color: var(--el-color-primary);
}

/* 校验与失败提示：轻微横向回弹，让注意力落到错误上而不是整页闪动 */
.login-error,
.caps {
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

@media (max-width: 900px) {
  .mobile-logo {
    display: block;
  }
}

/* 超窄屏两个尾部按钮会把输入区压到 40px 左右：清空是便利项，显隐是必需项，优先保留后者 */
@media (max-width: 360px) {
  .zy-input-action.is-clear {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-submit,
  .login-submit::after,
  .submit-arrow,
  .login-tags span {
    transition: none;
  }
  .login-error,
  .caps,
  .login-submit.is-busy {
    animation: none;
  }
  .login-submit:hover .submit-arrow {
    transform: none;
  }
  .login-submit:active {
    transform: none;
  }
  .login-submit::after {
    opacity: 0;
  }
}
</style>
