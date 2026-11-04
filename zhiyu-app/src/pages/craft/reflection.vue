<template><view class="growth-page" :class="{'motion-reduced':reducedMotion}"><view class="growth-hero"><text class="growth-eyebrow">A MOMENT FOR YOU</text><text class="growth-title">把这一刻，留给自己</text><text class="growth-subtitle">完成比完美重要。可以记录感受，也可以直接继续。</text></view><view v-if="error" class="growth-error">{{error}}</view><view class="growth-card"><FormField label="给创作取个名字" required :error="errors.title" :maxlength="100" :count="form.title.length"><template #default="{ id, ariaDescribedby, ariaInvalid }"><input :id="id" v-model="form.title" class="growth-input" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" maxlength="100" placeholder="例如：慢慢展开的春天" @input="errors.title = ''"/></template></FormField><text class="growth-card-title">开始前的感受（选填）</text><view class="growth-row"><view v-for="(m,i) in moods" :key="m" :class="['growth-choice',{selected:form.beforeMood===i+1}]" role="button" tabindex="0" @click="form.beforeMood=form.beforeMood===i+1?null:i+1" @keydown.enter.prevent="form.beforeMood=form.beforeMood===i+1?null:i+1" @keydown.space.prevent="form.beforeMood=form.beforeMood===i+1?null:i+1">{{m}}</view></view><text class="growth-card-title">现在的感受</text><view class="growth-row"><view v-for="(m,i) in moods" :key="m" :class="['growth-choice',{selected:form.afterMood===i+1}]" role="button" tabindex="0" @click="form.afterMood=i+1" @keydown.enter.prevent="form.afterMood=i+1" @keydown.space.prevent="form.afterMood=i+1">{{m}}</view></view><FormField label="情绪词" optional-text="选填" :error="errors.emotion" :maxlength="30" :count="form.emotion.length"><template #default="{ id, ariaDescribedby, ariaInvalid }"><input :id="id" v-model="form.emotion" class="growth-input" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" maxlength="30" placeholder="一个情绪词，例如：安定" @input="errors.emotion = ''"/></template></FormField><FormField label="创作笔记" optional-text="选填" :error="errors.note" :maxlength="500" :count="form.note.length"><template #default="{ id, ariaDescribedby, ariaInvalid }"><textarea :id="id" v-model="form.note" class="growth-input growth-textarea" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" maxlength="500" placeholder="创作时注意到了什么？" @input="errors.note = ''"/></template></FormField><checkbox-group @change="form.consent=$event.detail.value.length>0"><label class="growth-consent"><checkbox value="yes" :checked="form.consent" color="#217e76"/>同意加密保存本次感受，仅在我的成长记录中展示。</label></checkbox-group><AccessibleButton class="growth-button" :disabled="busy||!form.consent||!form.afterMood||!form.title.trim()" @click="save">保存感受</AccessibleButton></view><view class="growth-card ai-card"><view class="ai-heading"><text class="growth-card-title">让小织回看这次创作</text><text class="growth-muted">只发送作品名、步骤和自填感受，不发送对话。</text></view><checkbox-group @change="aiConsent=$event.detail.value.includes('ai')"><label class="growth-consent"><checkbox value="ai" :checked="aiConsent" color="#217e76"/>同意将本次创作信息发送给 AI 生成回顾</label></checkbox-group><AccessibleButton class="growth-button secondary" :disabled="summaryBusy||!canGenerate" @click="generateSummary">{{summaryBusy?'正在整理回顾…':summaryGenerated?'重新生成':'生成小织回顾'}}</AccessibleButton><text v-if="summaryError" class="growth-error" role="alert">{{summaryError}}</text><view v-if="summary" class="ai-summary"><FormField label="小织回顾" :error="errors.summary" :maxlength="300" :count="summary.length"><template #default="{ id, ariaDescribedby, ariaInvalid }"><textarea :id="id" v-model="summary" class="growth-input growth-textarea" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" maxlength="300" @input="errors.summary = ''"/></template></FormField><text class="growth-muted" :role="summaryGenerated?'status':undefined">{{summaryGenerated?'AI 生成，可修改；保存后仅本人可见。':'AI 暂时不可用，已使用固定回顾；可修改。'}}</text></view></view><AccessibleButton class="growth-button secondary" :disabled="busy" @click="publish">拍照 / 发布作品</AccessibleButton><AccessibleButton class="growth-button secondary" :disabled="busy" @click="home">返回首页</AccessibleButton><text class="growth-foot">本系统不能替代专业心理咨询</text><Toast/></view></template>
<script setup>
import {useInteraction} from '../../composables/useInteraction'
const {reducedMotion}=useInteraction()

import {computed,reactive,ref} from 'vue'
import {onLoad} from '@dcloudio/uni-app'
import {request} from '../../utils/request'
import Toast from '../../components/Toast.vue'
import FormField from '../../components/FormField.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import {useToast} from '../../composables/useToast'
const toast=useToast()
const busy=ref(false),error=ref(''),aiConsent=ref(false),summary=ref(''),summaryBusy=ref(false),summaryError=ref(''),summaryGenerated=ref(false)
const errors=ref({title:'',emotion:'',note:'',summary:''})
let projectId=''
const moods=['低落','偏低','平静','不错','开心']
const form=reactive({sessionId:'',title:'',beforeMood:null,afterMood:0,emotion:'',note:'',consent:false})
const canGenerate=computed(()=>!!form.sessionId&&!!form.title.trim()&&!!form.afterMood&&aiConsent.value&&!summaryBusy.value)
onLoad(o=>{form.sessionId=o.sessionId||'';projectId=o.projectId||''})
async function generateSummary(){
  if(!canGenerate.value)return
  summaryBusy.value=true
  summaryError.value=''
  try{
    const data=await request({url:'/api/growth/reflection/summary',method:'POST',data:{sessionId:form.sessionId,title:form.title,beforeMood:form.beforeMood,afterMood:form.afterMood,emotion:form.emotion,note:form.note,consent:aiConsent.value}})
    summary.value=data.summary||''
    summaryGenerated.value=!!data.generated
  }catch(e){
    summaryError.value=e.message||'回顾暂时没有生成，请重试。'
  }finally{
    summaryBusy.value=false
  }
}
async function save(){
  if(busy.value)return
  if(!form.title.trim()){errors.value.title='请给创作取个名字';return}
  busy.value=true
  error.value=''
  try{
    await request({url:'/api/growth/reflection',method:'POST',data:{...form,summary:summary.value.trim()}})
    toast.success('已保存')
    uni.redirectTo({url:'/pages/journey/index'})
  }catch(e){
    error.value=e.message
  }finally{
    busy.value=false
  }
}
function publish(){
  let url='/pages/work/publish?sessionId='+form.sessionId+'&projectId='+projectId
  if(summary.value.trim())url+='&message='+encodeURIComponent(summary.value.trim())
  uni.redirectTo({url})
}
function home(){uni.switchTab({url:'/pages/home/index'})}
</script>
<style src="../../styles/growth.css"></style>
<style scoped>.growth-choice{font-size:23rpx;padding:16rpx 12rpx;flex:1;text-align:center}.growth-row{gap:8rpx;margin-bottom:28rpx}.ai-heading{display:flex;flex-direction:column;gap:10rpx;margin-bottom:20rpx}.ai-summary{margin-top:24rpx;padding-top:20rpx;border-top:1rpx solid var(--zy-border,#dfe7dc)}.ai-count{display:block;text-align:right;font-size:22rpx;color:var(--zy-sub,#4a5f54);margin-top:8rpx}</style>
