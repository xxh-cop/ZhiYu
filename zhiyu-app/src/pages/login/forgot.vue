<template>
	<AccountShell :class="zyThemeClass" title="找回密码" eyebrow="别着急，慢慢来" description="两步完成：先申请一次性重置令牌，再设置新密码。">
    <!-- 发送通道未配置：明确展示真实状态，不进入第二步、不假装发送成功 -->
    <template v-if="unavailable">
      <view class="account-notice" role="alert">忘记密码功能暂未开放。请稍后再试，或联系学校管理员协助重置。</view>
      <AccessibleButton class="account-action" @click="backToLogin">返回登录</AccessibleButton>
    </template>

    <!-- 重置成功 -->
    <template v-else-if="resetDone">
      <view class="account-notice" role="status">密码已重置，请使用新密码登录。</view>
      <AccessibleButton class="account-action" @click="backToLogin">去登录</AccessibleButton>
    </template>

    <!-- 第一步：申请重置令牌 -->
    <template v-else-if="step===1">
      <AccountField id="forgot-username" ref="usernameField" v-model="form.username" label="登录名" placeholder="注册时使用的登录名" autocomplete="username" :maxlength="50" :disabled="loading" :error="errors.username" hint="若账号存在，重置指引将发送至其绑定通道（30 分钟内有效）；不会提示账号是否存在。" @confirm="apply"/>
      <view v-if="errMsg" class="account-notice error" role="alert">{{errMsg}}</view>
      <AccessibleButton class="account-action" :loading="loading" @click="apply">{{loading?'正在申请…':'申请重置令牌'}}</AccessibleButton>
    </template>

    <!-- 第二步：令牌 + 新密码 -->
    <template v-else>
      <view class="account-notice" role="status">若该账号存在，重置指引已发出（30 分钟内有效）。请查收后填写令牌。</view>
      <AccountField id="forgot-token" ref="tokenField" v-model="form.token" label="重置令牌" placeholder="粘贴收到的重置令牌" autocomplete="one-time-code" :maxlength="128" :disabled="loading" :error="errors.token"/>
      <AccountField id="forgot-new" v-model="form.newPassword" label="新密码" placeholder="6-32 位" autocomplete="new-password" secret :maxlength="32" :disabled="loading" :error="errors.newPassword"/>
      <AccountField id="forgot-confirm" v-model="form.confirm" label="确认新密码" placeholder="再次输入新密码" autocomplete="new-password" secret :maxlength="32" :disabled="loading" :error="errors.confirm" confirm-type="done" @confirm="reset"/>
      <view v-if="errMsg" class="account-notice error" role="alert">{{errMsg}}</view>
      <AccessibleButton class="account-action" :loading="loading" @click="reset">{{loading?'正在重置…':'重置密码'}}</AccessibleButton>
      <view class="account-links"><text>令牌不对或过期？</text><AccessibleButton class="account-link" :disabled="loading" @click="backToApply">重新申请</AccessibleButton></view>
    </template>

    <view class="account-divider"/>
    <view class="account-policy"><AccessibleButton class="account-link" @click="backToLogin">想起来了？返回登录</AccessibleButton></view>
  </AccountShell>
</template>
<script setup>
import {reactive,ref} from 'vue'
import AccountShell from '../../components/AccountShell.vue'
import AccountField from '../../components/AccountField.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import {request} from '../../utils/request'
/**
 * 忘记密码（AO 批，2026-11-04）。
 * 契约（与管理端 AL 批同源，后端语义不自造）：
 * - POST /api/auth/password/forgot：账号不存在也返回成功（不泄露存在性）；
 *   发送通道未配置 → 3001「忘记密码功能暂未开放」→ 明确展示，不进第二步；
 * - POST /api/auth/password/reset：令牌无效/过期 → 3001 原文展示，用户可回第一步重新申请；
 * - 后端无重发节流语义，本页不做倒计时；令牌只存内存表单，不写本地存储。
 */
const step=ref(1),loading=ref(false),unavailable=ref(false),resetDone=ref(false),errMsg=ref('')
const form=reactive({username:'',token:'',newPassword:'',confirm:''})
const errors=reactive({username:'',token:'',newPassword:'',confirm:''})
const usernameField=ref(null),tokenField=ref(null)
function backToLogin(){uni.navigateBack({delta:1,fail:()=>uni.reLaunch({url:'/pages/login/index'})})}
async function apply(){
  if(loading.value)return
  errMsg.value='';errors.username=form.username.trim()?'':'请填写登录名'
  if(errors.username){usernameField.value?.focus();return}
  loading.value=true
  try{
    await request({url:'/api/auth/password/forgot',method:'POST',auth:false,timeout:15000,data:{username:form.username.trim()}})
    step.value=2
  }catch(e){
    const msg=(e&&e.message)||''
    if(msg.includes('暂未开放'))unavailable.value=true
    else errMsg.value=msg||'申请未完成，请检查网络后重试。'
  }finally{loading.value=false}
}
async function reset(){
  if(loading.value)return
  errMsg.value=''
  errors.token=form.token.trim()?'':'请填写重置令牌'
  errors.newPassword=form.newPassword.length>=6&&form.newPassword.length<=32?'':'新密码长度需在 6-32 位之间'
  errors.confirm=form.newPassword===form.confirm?'':'两次输入的新密码不一致'
  if(errors.token){tokenField.value?.focus();return}
  if(errors.newPassword||errors.confirm)return
  loading.value=true
  try{
    await request({url:'/api/auth/password/reset',method:'POST',auth:false,timeout:15000,data:{token:form.token.trim(),newPassword:form.newPassword}})
    resetDone.value=true
  }catch(e){
    // 3001「重置令牌无效或已过期」等服务端原文直接展示，用户可点下方「重新申请」回到第一步
    errMsg.value=(e&&e.message)||'重置未完成，请稍后重试。'
  }finally{loading.value=false}
}
function backToApply(){step.value=1;errMsg.value='';form.token='';errors.token='';errors.newPassword='';errors.confirm=''}
</script>
<style scoped src="../../styles/account.css"></style>
