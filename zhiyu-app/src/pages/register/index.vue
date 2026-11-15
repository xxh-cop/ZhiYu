<template>
	<AccountShell :class="zyThemeClass" title="从这里开始" eyebrow="给自己留一点创作时间" description="先注册，再做第一件。">
    <view v-if="accountCreated" class="account-notice success" role="status">账号已创建。若自动登录未完成，可以重试登录，无需再次注册。</view>
    <AccountField id="register-username" ref="usernameField" v-model="form.username" label="登录名" placeholder="设置一个好记的登录名" hint="3–50 个字符，之后用它登录" autocomplete="username" :maxlength="50" :disabled="loading||accountCreated" :error="errors.username" @blur="validate('username')" @confirm="nicknameField?.focus()"/>
    <template v-if="!accountCreated">
      <AccountField id="register-nickname" ref="nicknameField" v-model="form.nickname" label="昵称" placeholder="你希望小织怎样称呼你" autocomplete="nickname" :maxlength="50" :disabled="loading" :error="errors.nickname" @blur="validate('nickname')" @confirm="schoolField?.focus()"/>
      <AccountField id="register-school" ref="schoolField" v-model="form.school" label="学校" placeholder="例如：江西师范大学" optional :maxlength="100" :disabled="loading" @confirm="passwordField?.focus()"/>
    </template>
    <AccountField id="register-password" ref="passwordField" v-model="form.password" label="密码" placeholder="设置登录密码" hint="6–32 个字符，建议组合字母、数字和符号" :autocomplete="accountCreated?'current-password':'new-password'" secret :maxlength="32" :disabled="loading" :error="errors.password" @blur="validate('password')" :confirm-type="accountCreated?'done':'next'" @confirm="accountCreated?handleRegister():confirmField?.focus()"/>
    <AccountField v-if="!accountCreated" id="register-confirm" ref="confirmField" v-model="form.confirmPassword" label="确认密码" placeholder="再次输入密码" autocomplete="new-password" secret :maxlength="32" :disabled="loading" :error="errors.confirmPassword" confirm-type="done" @blur="validate('confirmPassword')" @confirm="handleRegister"/>
    <view v-if="errMsg" class="account-notice error" role="alert">{{errMsg}}</view>
    <AccessibleButton class="account-action" :loading="loading" @click="handleRegister">{{loading?(accountCreated?'正在登录…':'正在创建账号…'):(accountCreated?'重试登录':'创建账号并登录')}}</AccessibleButton>
    <text class="account-school-hint">填写学校不代表完成身份核验。注册后可在「校园身份」中申请，由管理员审核。</text>
    <view class="account-links"><text>已有账号？</text><AccessibleButton class="account-link" :disabled="loading" @click="goLogin">返回登录</AccessibleButton></view>
    <view class="account-divider"/>
    <view class="account-policy"><AccessibleButton class="account-link" @click="goPrivacy">先了解隐私政策与用户协议</AccessibleButton></view>
    <text class="account-help">注册不会自动开启情绪数据处理或咨询师联系授权；这些权限将在使用时由你选择。</text>
  </AccountShell>
</template>
<script setup>
import {reactive,ref} from 'vue'
import {onLoad} from '@dcloudio/uni-app'
import AccountShell from '../../components/AccountShell.vue'
import AccountField from '../../components/AccountField.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import {safeWorkReturn} from '../../utils/work-navigation'
import {request} from '../../utils/request'
import {useUserStore} from '../../stores/user'
import {createRegistrationFlow} from '../../utils/registration-flow'
const userStore=useUserStore(),returnTo=ref(''),loading=ref(false),errMsg=ref(''),accountCreated=ref(false)
const form=reactive({username:'',nickname:'',school:'',password:'',confirmPassword:''})
const errors=reactive({username:'',nickname:'',password:'',confirmPassword:''})
const usernameField=ref(null),nicknameField=ref(null),schoolField=ref(null),passwordField=ref(null),confirmField=ref(null)
const fields={username:usernameField,nickname:nicknameField,password:passwordField,confirmPassword:confirmField}
const flow=createRegistrationFlow(request,()=>{accountCreated.value=true})
onLoad(query=>{returnTo.value=safeWorkReturn(query.next)})
function validate(field){
  const value=form[field]
  if(field==='username')errors[field]=value.trim().length<3?'登录名至少需要 3 个字符':''
  if(field==='nickname')errors[field]=value.trim()?'':'请填写昵称'
  if(field==='password')errors[field]=value.length<6||!value.trim()?'密码至少需要 6 个字符':''
  if(field==='confirmPassword')errors[field]=value===form.password&&value?'':'两次输入的密码不一致'
}
async function handleRegister(){
  if(loading.value)return
  errMsg.value=''
  const keys=accountCreated.value?['password']:Object.keys(errors)
  keys.forEach(validate)
  const invalid=keys.find(key=>errors[key]);if(invalid){fields[invalid].value?.focus();return}
  loading.value=true
  try{
    const data=await flow.submit({username:form.username.trim(),nickname:form.nickname.trim(),school:form.school.trim()||undefined,password:form.password})
    userStore.setToken(data.token);userStore.setUserInfo(data)
    uni.reLaunch({url:returnTo.value||'/pages/home/index'})
  }catch(e){
    if(accountCreated.value)errMsg.value='账号已经创建，但登录未完成。请检查网络或密码后重试登录。'
    else if(e.code===2002){errors.username='该登录名已被使用，可以换一个或返回登录。';usernameField.value?.focus()}
    else errMsg.value=e.code===-1?'网络连接中断，注册结果尚未确认。可先尝试登录，或稍后重试。':e.message||'注册未成功，请稍后重试。'
  }finally{loading.value=false}
}
function goLogin(){uni.redirectTo({url:'/pages/login/index'+(returnTo.value?'?next='+encodeURIComponent(returnTo.value):'')})}
function goPrivacy(){uni.navigateTo({url:'/pages/privacy/index'})}
</script>
<style scoped src="../../styles/account.css"></style>
