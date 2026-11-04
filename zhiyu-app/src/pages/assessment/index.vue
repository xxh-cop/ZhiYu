<template><view class="growth-page" :class="{'motion-reduced':reducedMotion}"><view class="growth-hero assess-hero"><view class="hero-copy"><text class="growth-eyebrow">SELF CHECK · 自我了解</text><text class="growth-title">给感受一个表达的空间</text><text class="growth-subtitle">自评只能帮助整理近期感受，不用于诊断。遇到困扰时，可以直接寻求专业支持。</text></view><XiaotuanPet :size="76" face="care" class="hero-pet" aria-hidden="true"/></view>
<view v-if="error" class="growth-error" role="alert">{{error}}<AccessibleButton v-if="!scales.length" class="growth-button secondary" @click="load">重新加载</AccessibleButton></view><view v-if="loading" class="growth-card" aria-busy="true"><view class="growth-skeleton"/><view class="growth-skeleton short"/><text class="growth-muted">正在加载…</text></view>
<template v-if="!active"><view v-for="s in scales" :key="s.id" class="growth-card scale-card"><view class="scale-badge" aria-hidden="true"><text class="scale-badge-text">{{s.questions.length}} 题</text></view><view class="scale-copy"><text class="growth-card-title">{{s.title}}</text><text class="growth-muted">回顾{{s.period}} · 可保存进度</text></view><AccessibleButton class="growth-button scale-action" @click="select(s)">开始 / 继续</AccessibleButton></view><view class="growth-card"><text class="growth-muted">PSS-10、PANAS、GSES 将在中文版本及使用授权核验完成后开放。</text></view></template>
<template v-else-if="!result"><view class="growth-card"><text class="growth-card-title">{{active.title}}</text><text class="growth-muted">{{active.attribution}}</text><text class="growth-muted source" role="button" tabindex="0" @click="copySource" @keydown.enter.prevent="copySource" @keydown.space.prevent="copySource">查看来源：点此复制原量表链接</text><checkbox-group @change="consent=$event.detail.value.length>0"><label class="growth-consent"><checkbox value="yes" :checked="consent" color="#217e76"/>我了解自评的局限，同意加密保存本次答案与结果；出现安全信号时按平台关怀流程处理。可在成长旅程清除记录。</label></checkbox-group></view>
<view v-if="consent" class="growth-card"><view class="growth-row growth-spread"><text class="growth-tag">{{index+1}} / {{active.questions.length}}</text><text class="growth-muted">{{active.period}}</text></view><view class="growth-progress" role="progressbar" :aria-valuenow="answered" :aria-valuemax="answers.length" aria-label="作答进度"><view class="growth-progress-fill" :style="{width:(answered/answers.length*100)+'%'}"/></view><text class="growth-muted">已回答 {{answered}} / {{answers.length}} 题</text><view :key="index" class="question-step"><text class="question">{{active.questions[index]}}</text><AccessibleButton v-for="(o,i) in active.options" :aria-pressed="answers[index]===i" :disabled="busy" :key="i" :class="['growth-choice',{selected:answers[index]===i}]" @click="choose(i)">{{o}}</AccessibleButton></view><view class="growth-row"><AccessibleButton class="growth-button secondary" :disabled="index===0||busy" @click="index--">上一题</AccessibleButton><AccessibleButton v-if="index<answers.length-1" class="growth-button" :disabled="answers[index]<0||busy" @click="index++">下一题</AccessibleButton><AccessibleButton v-else class="growth-button" :disabled="answers.includes(-1)||busy" @click="submit(false)">查看结果</AccessibleButton></view><AccessibleButton class="growth-button secondary" :disabled="busy" @click="submit(true)">保存进度，稍后继续</AccessibleButton></view><AccessibleButton class="growth-button secondary" :disabled="busy" @click="leave">返回量表列表</AccessibleButton></template>
<view v-else class="growth-card"><text class="growth-card-title">本次记录 · {{result.score}} 分</text><text class="growth-muted">参考分数区间：{{result.interval}}。{{result.message}}</text><text class="growth-subtitle">单次分数不能判断心理疾病，也不代表你的价值。若困扰持续或影响生活，请寻求专业支持。</text><AccessibleButton class="growth-button" @click="journey">查看我的成长旅程</AccessibleButton><AccessibleButton class="growth-button secondary" @click="active=null;result=null">完成</AccessibleButton></view><view class="support-card"><text class="support-text">如果此刻有些撑不住，可以直接找到支持渠道。</text><AccessibleButton class="support-link" @click="help">我需要支持 →</AccessibleButton></view><text class="growth-foot">本系统不能替代专业心理咨询</text><ActionSheet :open="pendingLeave" title="返回列表" description="未保存的作答将丢失。可先选择保存进度。" :actions="[{key:'confirm',label:'放弃并返回',tone:'danger'}]" cancel-text="继续作答" @select="confirmLeave" @close="pendingLeave=false"/><Toast/></view></template>
<script setup>
import {useInteraction} from '../../composables/useInteraction'
import ActionSheet from '../../components/ActionSheet.vue'
import Toast from '../../components/Toast.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import XiaotuanPet from '../../components/XiaotuanPet.vue'
import {useToast} from '../../composables/useToast'
const {reducedMotion}=useInteraction()
const toast=useToast()

import {ref,computed} from 'vue';import {onLoad} from '@dcloudio/uni-app';import {request} from '../../utils/request'
const scales=ref([]),active=ref(null),answers=ref([]),index=ref(0),consent=ref(false),busy=ref(false),loading=ref(false),error=ref(''),result=ref(null);let requested='';let drafts=[]
const answered=computed(()=>answers.value.filter(n=>n>=0).length)
async function load(){loading.value=true;error.value='';try{scales.value=await request({url:'/api/growth/scales'});const j=await request({url:'/api/growth/journey'});drafts=j.records.filter(r=>r.kind==='DRAFT');if(requested){const s=scales.value.find(s=>s.id===requested);if(s)select(s);requested=''}}catch(e){error.value=e.message}finally{loading.value=false}}
function select(s){active.value=s;const d=drafts.find(d=>d.scale===s.id&&d.version===s.version&&d.answers?.length===s.questions.length);answers.value=d?[...d.answers]:s.questions.map(()=>-1);index.value=Math.max(0,answers.value.indexOf(-1));consent.value=false;result.value=null;error.value=''}
function choose(i){if(busy.value)return;answers.value[index.value]=i;if(active.value.id==='PHQ9'&&index.value===8&&i>0){help();submit(true,true)}}
async function submit(draft,crisis=false){if(busy.value||!consent.value)return;busy.value=true;error.value='';try{const r=await request({url:'/api/growth/assessment',method:'POST',data:{scale:active.value.id,version:active.value.version,answers:answers.value,consent:true,draft}});if(r.crisis&&!crisis){help();return}if(!draft)result.value=r;else if(!crisis){toast.success('进度已保存');active.value=null;await load()}}catch(e){error.value=e.message}finally{busy.value=false}}
const pendingLeave=ref(false)
function leave(){pendingLeave.value=true}
function confirmLeave(){pendingLeave.value=false;active.value=null}
function copySource(){uni.setClipboardData({data:active.value.source})}function help(){uni.navigateTo({url:'/pages/crisis/index'})}function journey(){uni.navigateTo({url:'/pages/journey/index'})}onLoad(o=>{requested=o.scale||'';load()})
</script><style src="../../styles/growth.css">
.source:focus-visible{outline:3px solid var(--zy-primary,#217e76);outline-offset:3px}
</style><style scoped>
/* AZ 精修（2026-11-05）：hero 补小织、量表卡加徽章层次、支持入口改温和关怀卡。结构/流程不动。 */
.assess-hero{display:flex;align-items:center;gap:16rpx}.hero-copy{flex:1;min-width:0}.hero-pet{flex-shrink:0}
.scale-card{display:flex;align-items:center;gap:22rpx}
.scale-badge{flex-shrink:0;width:84rpx;height:84rpx;border-radius:24rpx;background:var(--zy-bg-tint-mint,#e8efe4);display:flex;align-items:center;justify-content:center}
.scale-badge-text{font-size:24rpx;font-weight:600;color:var(--zy-brand-700,#1a645e)}
.scale-copy{flex:1;min-width:0}
.scale-action{flex-shrink:0;margin:0}
.question{display:block;font-size:34rpx;line-height:1.8;margin:36rpx 0}.source{display:block;color:#217e76;margin-top:20rpx}.growth-row button{flex:1}
.source:focus-visible{outline:3px solid var(--zy-primary,#217e76);outline-offset:3px}
.support-card{display:flex;align-items:center;justify-content:space-between;gap:16rpx;margin-top:32rpx;padding:24rpx 28rpx;background:var(--zy-card-bg,#ffffff);border:1rpx solid var(--zy-border,#dfe7dc);border-radius:22rpx}
.support-text{font-size:24rpx;line-height:1.7;color:var(--zy-text,#4a5f54)}
.support-link{flex-shrink:0;margin:0;padding:8rpx 20rpx;background:transparent;color:var(--zy-brand-700,#1a645e);font-size:24rpx}
</style>
