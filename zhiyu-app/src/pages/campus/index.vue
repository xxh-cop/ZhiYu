<script setup>
import {ref} from 'vue'
import {onShow} from '@dcloudio/uni-app'
import {request} from '../../utils/request'
import Toast from '../../components/Toast.vue'
import FormField from '../../components/FormField.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import XiaotuanPet from '../../components/XiaotuanPet.vue'
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
<template><view class="campus-page"><view class="campus-hero"><view class="hero-copy"><text class="eyebrow">校园身份</text><text class="title">让校园连接更可信</text><text class="intro">提交信息后由管理员核实。身份审核不会改变账号权限，姓名和学号仅用于审核。</text></view><XiaotuanPet :size="76" face="calm" class="hero-pet" aria-hidden="true"/></view><view v-if="loading" class="campus-card"><text class="intro">正在读取申请…</text></view><template v-else><view v-if="current" class="campus-card status-card" :class="'status-' + (current.status || '').toLowerCase()"><text class="status-label">{{labels[current.status]}}</text><text v-if="current.review_reason" class="status-reason">{{current.review_reason}}</text></view><view v-if="!current||current.status==='REJECTED'" class="campus-card"><FormField label="学校" required :error="errors.school" :maxlength="100" :count="(form.school || '').length"><template #default="{ id, ariaDescribedby, ariaInvalid }"><input :id="id" class="campus-input" :class="{ 'is-focused': focusedField==='school' }" v-model="form.school" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" maxlength="100" placeholder="学校全称" @input="errors.school = ''" @focus="focusedField='school'" @blur="focusedField=''"/></template></FormField><FormField label="学院" required :error="errors.college" :maxlength="100" :count="(form.college || '').length"><template #default="{ id, ariaDescribedby, ariaInvalid }"><input :id="id" class="campus-input" :class="{ 'is-focused': focusedField==='college' }" v-model="form.college" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" maxlength="100" placeholder="所在学院" @input="errors.college = ''" @focus="focusedField='college'" @blur="focusedField=''"/></template></FormField><FormField label="姓名" required :error="errors.realName" :maxlength="40" :count="(form.realName || '').length"><template #default="{ id, ariaDescribedby, ariaInvalid }"><input :id="id" class="campus-input" :class="{ 'is-focused': focusedField==='realName' }" v-model="form.realName" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" maxlength="40" placeholder="与校园登记一致的姓名" @input="errors.realName = ''" @focus="focusedField='realName'" @blur="focusedField=''"/></template></FormField><FormField label="学号" required :error="errors.studentNumber" :maxlength="40" :count="(form.studentNumber || '').length"><template #default="{ id, ariaDescribedby, ariaInvalid }"><input :id="id" class="campus-input" :class="{ 'is-focused': focusedField==='studentNumber' }" v-model="form.studentNumber" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" maxlength="40" placeholder="请输入学号" @input="errors.studentNumber = ''" @focus="focusedField='studentNumber'" @blur="focusedField=''"/></template></FormField><AccessibleButton class="submit" :loading="saving" :disabled="saving" @click="submit">{{current?'重新提交':'提交审核'}}</AccessibleButton></view></template><view v-if="error" class="error-card" role="alert"><text class="error">{{error}}</text><AccessibleButton class="error-retry" @click="load">重新加载</AccessibleButton></view><text class="disclaimer">本系统不能替代专业心理咨询</text><Toast/></view></template>
<style scoped>
/* AZ 视觉深化（2026-11-05）：品牌 hero + 令牌化 + 状态分级 + 表单与全站白底统一。功能不动。 */
.campus-page { padding: 32rpx 28rpx 80rpx; background: var(--zy-bg, #f5f7f2); min-height: 100vh; box-sizing: border-box; }
.campus-hero { display: flex; align-items: center; gap: 16rpx; padding: 32rpx; margin-bottom: 28rpx; background: linear-gradient(120deg, var(--zy-bg-tint-mint, #e8efe4), var(--zy-bg-tint-warm, #f7efd9)); border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 28rpx; }
.hero-copy { flex: 1; min-width: 0; }
.hero-pet { flex-shrink: 0; }
.eyebrow { display: block; font-size: 22rpx; letter-spacing: 3rpx; color: var(--zy-brand-700, #1a645e); }
.title { display: block; font-family: var(--zy-serif); font-size: 42rpx; font-weight: 600; color: var(--zy-title, #233d34); margin-top: 10rpx; }
.intro { display: block; font-size: 25rpx; line-height: 1.8; color: var(--zy-text, #4a5f54); margin-top: 12rpx; }
.campus-card { padding: 32rpx; background: var(--zy-card-bg, #ffffff); border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 28rpx; box-shadow: var(--zy-shadow-sm, 0 4rpx 16rpx rgba(35, 61, 52, 0.04)); margin-bottom: 24rpx; }
/* class 落在 uni-input 包装元素上：包装定高 + flex 居中，内层运行时 input 用 :deep 着色 */
.campus-input { padding: 0 24rpx; background: var(--zy-card-bg, #ffffff); border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 16rpx; box-sizing: border-box; width: 100%; height: 84rpx; display: flex; align-items: center; }
.campus-input :deep(input) { font-size: 28rpx; color: var(--zy-title, #233d34); }
.campus-input.is-focused { border-color: var(--zy-brand-600, #217e76); }
.submit { margin-top: 12rpx; width: 100%; background: var(--zy-brand-600, #1A645E); color: #ffffff; border-radius: var(--zy-radius-full, 9999rpx); font-weight: 600; box-shadow: var(--zy-shadow-md, 0 8rpx 24rpx rgba(33, 126, 118, 0.18)); }
/* 状态分级：审核中=暖底 / 已通过=薄荷底 / 未通过=绛红边 */
.status-card { gap: 12rpx; }
.status-label { font-size: 30rpx; font-weight: 600; }
.status-reason { font-size: 25rpx; line-height: 1.8; color: var(--zy-text, #4a5f54); }
.status-pending { background: var(--zy-bg-tint-warm, #f7efd9); border-color: var(--zy-border, #dfe7dc); }
.status-pending .status-label { color: var(--zy-warning-text, #8a6d1f); }
.status-approved { background: var(--zy-bg-tint-mint, #e8efe4); }
.status-approved .status-label { color: var(--zy-brand-700, #1a645e); }
.status-rejected { background: var(--zy-card-bg, #ffffff); border-color: var(--zy-care, #a64842); }
.status-rejected .status-label { color: var(--zy-care, #a64842); }
.error-card { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 24rpx 28rpx; background: var(--zy-card-bg, #ffffff); border: 1rpx solid var(--zy-care, #a64842); border-radius: 20rpx; }
.error { color: var(--zy-care, #a64842); font-size: 26rpx; }
.error-retry { flex-shrink: 0; margin: 0; padding: 8rpx 24rpx; background: transparent; color: var(--zy-brand-700, #1a645e); font-size: 24rpx; }
.disclaimer { display: block; text-align: center; margin-top: 36rpx; font-size: 24rpx; color: var(--zy-text, #4a5f54); }
button:focus-visible { outline: 3px solid var(--zy-brand-700, #1a645e); outline-offset: 3px; }
</style>
