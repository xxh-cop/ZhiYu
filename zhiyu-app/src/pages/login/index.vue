<template>
  <AccountShell :class="zyThemeClass" title="欢迎回来" eyebrow="和小织，继续今天的故事" description="织愈是一个用 AI 陪伴 + 非遗手作，帮你把情绪慢下来的学生工具。登录后继续。">
    <view v-if="returnTo" class="account-notice">登录后将返回你刚才查看的内容。</view>
    <view v-if="restoring" class="account-notice" role="status">正在确认登录状态…</view>
    <template v-else-if="restoreFailed">
      <view class="account-notice error" role="alert">暂时无法确认登录状态，请检查网络后重试。</view>
      <AccessibleButton class="account-action" @click="restoreSession">重试连接</AccessibleButton>
      <AccessibleButton class="account-action secondary" @click="switchAccount">使用其他账号</AccessibleButton>
    </template>
    <template v-else>
      <AccountField id="login-username" ref="usernameField" v-model="form.username" label="登录名" placeholder="注册时使用的登录名" autocomplete="username" :maxlength="50" :disabled="loading" :error="errors.username" @confirm="passwordField?.focus()"/>
      <AccountField id="login-password" ref="passwordField" v-model="form.password" label="密码" placeholder="请输入密码" autocomplete="current-password" secret :maxlength="32" :disabled="loading" :error="errors.password" confirm-type="done" @confirm="handleLogin"/>
      <view v-if="errMsg" class="account-notice error" role="alert">{{errMsg}}</view>
      <AccessibleButton class="account-action" :loading="loading" @click="handleLogin">{{loading?'正在登录…':'登录织愈'}}</AccessibleButton>
      <view class="account-links"><text>第一次来到这里？</text><AccessibleButton class="account-link" :disabled="loading" @click="goRegister">创建账号</AccessibleButton><AccessibleButton class="account-link" :disabled="loading" @click="goForgot">忘记密码？</AccessibleButton></view>
    </template>
    <view class="account-divider"/>
    <text class="account-help">使用平台注册账号登录。校园身份可在登录后申请人工核验；账号问题请联系平台管理员。</text>
    <view class="account-policy"><AccessibleButton class="account-link" @click="goPrivacy">隐私政策与用户协议</AccessibleButton></view>
  </AccountShell>
</template>
<script setup>
import {reactive,ref} from 'vue'
import {onLoad,onShow} from '@dcloudio/uni-app'
import AccountShell from '../../components/AccountShell.vue'
import AccountField from '../../components/AccountField.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import {safeWorkReturn} from '../../utils/work-navigation'
import {request} from '../../utils/request'
import {useUserStore} from '../../stores/user'
const userStore=useUserStore()
const form=reactive({username:'',password:''}),errors=reactive({username:'',password:''})
const loading=ref(false),errMsg=ref(''),returnTo=ref(''),restoring=ref(false),restoreFailed=ref(false)
const usernameField=ref(null),passwordField=ref(null)
onLoad(query=>{returnTo.value=safeWorkReturn(query.next)})
onShow(()=>{if(userStore.isLogin&&!restoreFailed.value)restoreSession()})
function enter(){uni.reLaunch({url:returnTo.value||'/pages/home/index'})}
async function restoreSession(){
  if(restoring.value)return
  restoring.value=true;restoreFailed.value=false
  try{const info=await request({url:'/api/auth/me',timeout:15000});userStore.setUserInfo(info);enter()}
  catch(e){if(e.code===1002||e.code===1003){userStore.clearAuth();errMsg.value='登录已失效，请重新登录。'}else restoreFailed.value=true}
  finally{restoring.value=false}
}
function switchAccount(){userStore.clearAuth();restoreFailed.value=false;errMsg.value=''}
async function handleLogin(){
  if(loading.value)return
  errMsg.value='';errors.username=form.username.trim()?'':'请填写登录名';errors.password=form.password?'':'请填写密码'
  if(errors.username||errors.password){(errors.username?usernameField:passwordField).value?.focus();return}
  loading.value=true
  try{
    const data=await request({url:'/api/auth/login',method:'POST',auth:false,timeout:15000,data:{username:form.username,password:form.password}})
    userStore.setToken(data.token);userStore.setUserInfo(data);enter()
  }catch(e){errMsg.value=e.code===-1?'暂时无法连接，请检查网络后重试。':e.message||'登录未成功，请稍后重试。'}
  finally{loading.value=false}
}
function goRegister(){uni.navigateTo({url:'/pages/register/index'+(returnTo.value?'?next='+encodeURIComponent(returnTo.value):'')})}
function goForgot(){uni.navigateTo({url:'/pages/login/forgot'})}
function goPrivacy(){uni.navigateTo({url:'/pages/privacy/index'})}
</script>
<style scoped src="../../styles/account.css"></style>
