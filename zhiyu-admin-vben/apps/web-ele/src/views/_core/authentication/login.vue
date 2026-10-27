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
<template><div class="login-form"><img class="mobile-logo" src="/logo.svg" alt="织愈"/><p class="login-kicker">WELCOME BACK</p><h2>欢迎回到织愈</h2><p class="login-desc">登录工作空间，开始今天的创作与协作。</p><form @submit.prevent="submit"><label for="zy-user">工作账号</label><div class="input-wrap"><span aria-hidden="true">◎</span><input id="zy-user" ref="usernameInput" :aria-invalid="invalid.username" :aria-describedby="error?'login-error':undefined" autocapitalize="none" :spellcheck="false" maxlength="50" v-model="form.username" name="username" autocomplete="username" placeholder="请输入账号" :disabled="authStore.loginLoading"/></div><label for="zy-password">登录密码</label><div class="input-wrap"><span aria-hidden="true">⌑</span><input id="zy-password" ref="passwordInput" :aria-invalid="invalid.password" :aria-describedby="error?'login-error':undefined" maxlength="32" v-model="form.password" name="password" :type="visible?'text':'password'" autocomplete="current-password" placeholder="请输入密码" :disabled="authStore.loginLoading" @keyup="caps=$event.getModifierState('CapsLock')" @blur="caps=false"/><button class="reveal" type="button" :disabled="authStore.loginLoading" :aria-label="visible?'隐藏密码':'显示密码'" :aria-pressed="visible" @click="visible=!visible">{{visible?'隐藏':'显示'}}</button></div><p v-if="caps" class="caps">大写锁定已开启</p><p v-if="error" id="login-error" class="login-error" role="alert">{{error}}</p><button class="login-submit" type="submit" :disabled="authStore.loginLoading">{{authStore.loginLoading?'正在登录…':'登录工作空间'}} <span v-if="!authStore.loginLoading">→</span></button></form><div class="login-divider"></div><p class="login-help">供管理员、传承人与咨询师使用。<br/>账号问题请联系平台管理员。</p><div class="login-tags"><span>内容管理</span><i></i><span>活动协作</span><i></i><span>授权访问</span></div></div></template>
<style scoped>.login-form{color:var(--el-text-color-primary)}.mobile-logo{display:none;width:52px;margin-bottom:26px}.login-kicker{font-size:10px;letter-spacing:3px;color:var(--el-text-color-regular);margin-bottom:18px}.login-form h2{font-size:30px;font-weight:550;letter-spacing:1px}.login-desc{font-size:12px;color:var(--el-text-color-regular);margin-top:13px;margin-bottom:40px}.login-form label{display:block;font-size:12px;font-weight:550;margin:24px 0 10px}.input-wrap{display:flex;align-items:center;gap:12px;height:52px;padding:0 15px;background:var(--el-bg-color);border:1px solid var(--el-border-color);border-radius:10px;transition:border-color .2s,box-shadow .2s}.input-wrap:focus-within{border-color:var(--el-color-primary);box-shadow:0 0 0 3px color-mix(in srgb,var(--el-color-primary) 12%,transparent)}.input-wrap>span{color:var(--el-text-color-placeholder);font-size:21px}.input-wrap input{flex:1;min-width:0;background:transparent;font-size:16px;outline:none;color:var(--el-text-color-primary);height:100%}.input-wrap input::placeholder{color:var(--el-text-color-placeholder)}.reveal{font-size:11px;color:var(--el-text-color-regular);padding:6px}.login-submit{display:flex;justify-content:space-between;align-items:center;background:var(--el-color-primary);color:var(--el-color-white);width:100%;height:52px;margin-top:30px;padding:0 20px;border-radius:10px;font-size:13px;box-shadow:0 7px 15px color-mix(in srgb,var(--el-color-primary) 12%,transparent);transition:background .2s}.login-submit:hover{background:var(--el-color-primary-dark-2)}.login-submit:disabled{opacity:.6}.login-divider{height:1px;background:var(--el-border-color-lighter);margin:32px 0 20px}.login-help{font-size:11px;line-height:2;color:var(--el-text-color-regular)}.login-tags{display:flex;gap:13px;align-items:center;font-size:10px;color:var(--el-text-color-regular);margin-top:20px}.login-tags i{width:3px;height:3px;background:var(--el-border-color);border-radius:50%}.login-error,.caps{font-size:12px;color:var(--el-color-danger);margin-top:12px;line-height:1.7}button:focus-visible{outline:3px solid var(--el-color-primary);outline-offset:3px}@media(max-width:900px){.mobile-logo{display:block}}
.input-wrap:has(input[aria-invalid="true"]){border-color:var(--el-color-danger)}.reveal{min-width:44px;min-height:44px}@media(prefers-reduced-motion:reduce){.input-wrap,.login-submit{transition:none}}</style>
