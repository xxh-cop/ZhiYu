<script setup>
import {ref} from 'vue'
import {onShow} from '@dcloudio/uni-app'
import {request} from '../../utils/request'
import Toast from '../../components/Toast.vue'
import FormField from '../../components/FormField.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import {useToast} from '../../composables/useToast'
const toast=useToast()
const current=ref(null),loading=ref(false),saving=ref(false),error=ref('')
const errors=ref({school:'',college:'',realName:'',studentNumber:''})
const form=ref({school:'江西师范大学',college:'人工智能学院',realName:'',studentNumber:''})
const focusedField=ref('')
const labels={PENDING:'审核中',APPROVED:'已通过',REJECTED:'未通过'}
async function load(){loading.value=true;error.value='';try{current.value=await request({url:'/api/campus/verification'});if(current.value){form.value={school:current.value.school,college:current.value.college,...current.value.identity}}}catch(e){error.value=e.message||'加载失败'}finally{loading.value=false}}
async function submit(){errors.value={school:'',college:'',realName:'',studentNumber:''};if(!form.value.school?.trim())errors.value.school='请填写学校';if(!form.value.college?.trim())errors.value.college='请填写学院';if(!form.value.realName?.trim())errors.value.realName='请填写姓名';if(!form.value.studentNumber?.trim())errors.value.studentNumber='请填写学号';if(Object.values(errors.value).some(Boolean))return;saving.value=true;error.value='';try{await request({url:'/api/campus/verification',method:'POST',data:form.value});toast.success('已提交审核');await load()}catch(e){error.value=e.message||'提交失败'}finally{saving.value=false}}
onShow(load)
</script>
<template><view :class="zyThemeClass" class="campus-page"><view class="campus-hero"><view class="hero-copy"><text class="eyebrow">STUDENT IDENTITY · 校内身份</text><text class="title">让校园连接更可信</text><text class="intro">提交信息后由管理员核实。身份审核不会改变账号权限，姓名和学号仅用于审核。</text></view></view><view v-if="loading" class="campus-card"><text class="intro">正在读取申请…</text></view><template v-else><view v-if="current" class="campus-card status-card" :class="'status-' + (current.status || '').toLowerCase()"><text class="status-label">{{labels[current.status]}}</text><text v-if="current.review_reason" class="status-reason">{{current.review_reason}}</text></view><view v-if="!current||current.status==='REJECTED'" class="campus-card"><FormField label="学校" required :error="errors.school" :maxlength="100" :count="(form.school || '').length"><template #default="{ id, ariaDescribedby, ariaInvalid }"><input :id="id" class="campus-input" :class="{ 'is-focused': focusedField==='school' }" v-model="form.school" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" maxlength="100" placeholder="学校全称" @input="errors.school = ''" @focus="focusedField='school'" @blur="focusedField=''"/></template></FormField><FormField label="学院" required :error="errors.college" :maxlength="100" :count="(form.college || '').length"><template #default="{ id, ariaDescribedby, ariaInvalid }"><input :id="id" class="campus-input" :class="{ 'is-focused': focusedField==='college' }" v-model="form.college" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" maxlength="100" placeholder="所在学院" @input="errors.college = ''" @focus="focusedField='college'" @blur="focusedField=''"/></template></FormField><FormField label="姓名" required :error="errors.realName" :maxlength="40" :count="(form.realName || '').length"><template #default="{ id, ariaDescribedby, ariaInvalid }"><input :id="id" class="campus-input" :class="{ 'is-focused': focusedField==='realName' }" v-model="form.realName" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" maxlength="40" placeholder="与校园登记一致的姓名" @input="errors.realName = ''" @focus="focusedField='realName'" @blur="focusedField=''"/></template></FormField><FormField label="学号" required :error="errors.studentNumber" :maxlength="40" :count="(form.studentNumber || '').length"><template #default="{ id, ariaDescribedby, ariaInvalid }"><input :id="id" class="campus-input" :class="{ 'is-focused': focusedField==='studentNumber' }" v-model="form.studentNumber" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" maxlength="40" placeholder="请输入学号" @input="errors.studentNumber = ''" @focus="focusedField='studentNumber'" @blur="focusedField=''"/></template></FormField><AccessibleButton class="submit" :loading="saving" :disabled="saving" @click="submit">{{current?'重新提交':'提交审核'}}</AccessibleButton></view></template><view v-if="error" class="error-card" role="alert"><text class="error">{{error}}</text><AccessibleButton class="error-retry" @click="load">重新加载</AccessibleButton></view><text class="disclaimer">本系统不能替代专业心理咨询</text><Toast/></view></template>
<style scoped>
/* AZ 视觉深化（2026-11-05）：品牌 hero + 令牌化 + 状态分级 + 表单与全站白底统一。功能不动。 */
.campus-page { padding: 36rpx 32rpx 80rpx; background: var(--zy-bg, #f1f4f1); min-height: 100vh; box-sizing: border-box; }
.campus-hero { align-items: flex-start; gap: 16rpx; padding: 0 0 28rpx; margin-bottom: 0; background: transparent; border: 0; border-bottom: 1rpx solid var(--zy-border, #d3ded8); border-radius: 0; }
.hero-copy { flex: 1; min-width: 0; }
.hero-pet { flex-shrink: 0; }
.eyebrow { display: block; font-size:var(--zy-text-micro, 22rpx); letter-spacing: 2px; color: var(--zy-muted, #6e7b75); }
.title { display: block; font-family: var(--zy-font-display, serif); font-size:56rpx; font-weight: 500; color: var(--zy-title, #111816); margin-top: 10rpx; line-height: 1.35; }
.intro { display: block; font-size:var(--zy-text-caption, 24rpx); line-height: 1.8; color: var(--zy-muted, #6e7b75); margin-top: 12rpx; }
.campus-card { padding: 28rpx 0; background: transparent; border: 0; border-top: 1rpx solid var(--zy-border, #d3ded8); border-radius: 0; box-shadow: none; margin-bottom: 0; }
.campus-input { padding: 0; background: transparent; border: 0; border-bottom: 1rpx solid var(--zy-border, #d3ded8); border-radius: 0; width: 100%; height: 72rpx; display: flex; align-items: center; }
.campus-input :deep(input) { font-size:var(--zy-text-body, 28rpx); color: var(--zy-title, #111816); }
.campus-input.is-focused { border-color: var(--zy-brand-600, #0e5b53); }
.submit { margin-top: 12rpx; width: 100%; background: var(--zy-brand-600, #0e5b53); color: #ffffff; border-radius: 0; font-weight: 500; box-shadow: none; }
.status-card { gap: 12rpx; border-left: 3rpx solid var(--zy-border, #d3ded8); padding-left: 20rpx; }
.status-label { font-family: var(--zy-font-display, serif); font-size:var(--zy-text-body-lg, 30rpx); font-weight: 500; }
.status-reason { font-size:var(--zy-text-caption, 24rpx); line-height: 1.8; color: var(--zy-muted, #6e7b75); }
.status-pending { background: transparent; border-color: var(--zy-border, #d3ded8); border-left-color: var(--zy-accent, #a64842); }
.status-pending .status-label { color: var(--zy-accent, #a64842); }
.status-approved { background: transparent; border-left-color: var(--zy-brand-600, #0e5b53); }
.status-approved .status-label { color: var(--zy-brand-700, #0a403b); }
.status-rejected { background: transparent; border-left-color: var(--zy-care, #a64842); }
.status-rejected .status-label { color: var(--zy-care, #a64842); }
.error-card { gap: 16rpx; padding: 20rpx 0 20rpx 18rpx; background: transparent; border: 0; border-left: 3rpx solid var(--zy-care, #a64842); border-radius: 0; }
.error { color: var(--zy-care, #a64842); font-size:var(--zy-text-caption, 24rpx); }
.error-retry { flex-shrink: 0; margin: 0; padding: 8rpx 0; background: transparent; color: var(--zy-brand-700, #0a403b); font-size:var(--zy-text-caption, 24rpx); }
.disclaimer { display: block; text-align: center; margin-top: 36rpx; font-size:var(--zy-text-caption, 24rpx); color: var(--zy-muted, #6e7b75); }
button:focus-visible { outline: 3px solid var(--zy-brand-700, var(--zy-brand-700, #1a645e)); outline-offset: 3px; }
</style>
