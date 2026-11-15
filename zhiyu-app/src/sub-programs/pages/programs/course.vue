<script setup>
import {computed,ref} from 'vue'
import {onLoad} from '@dcloudio/uni-app'
import {request} from '@/utils/request'
import AccessibleButton from '@/components/AccessibleButton.vue'
const id=ref(''),rows=ref([]),loading=ref(false),saving=ref(false),error=ref(''),selected=ref('')
const active=computed(()=>rows.value.find(r=>r.id===selected.value)),completed=computed(()=>rows.value.filter(r=>Number(r.completed)===1).length)
async function load(){loading.value=true;error.value='';try{rows.value=await request({url:'/api/growth/programs/'+id.value+'/lessons'});if(!active.value)selected.value=rows.value[0]?.id||''}catch(e){error.value='课程加载失败，请重试'}finally{loading.value=false}}
async function mark(){if(saving.value||!active.value)return;const lesson=active.value;saving.value=true;try{await request({url:'/api/growth/programs/'+id.value+'/lessons/'+lesson.id+'/progress',method:'PUT',data:{completed:Number(lesson.completed)!==1}});await load()}catch(e){error.value='课程进度保存失败，请重试'}finally{saving.value=false}}
function practice(){if(active.value?.project_id)uni.navigateTo({url:'/pages/craft/detail?id='+active.value.project_id})}
onLoad(options=>{id.value=options.id||'';if(!/^\d+$/.test(id.value)){error.value='课程编号无效';return}load()})
</script>
<template><view :class="zyThemeClass" class="growth-page"><view class="growth-hero"><text class="growth-eyebrow">循序学习</text><text class="growth-title">我的课程</text><text class="growth-subtitle">跟随自己的节奏，每次学会一点点。</text></view><view v-if="error" class="growth-error" role="alert">{{error}}<AccessibleButton class="growth-button secondary" @click="load">重新加载</AccessibleButton></view><text v-if="loading" class="growth-muted">正在读取课程…</text><template v-else><view class="growth-card"><text class="growth-card-title">学习进度 · {{completed}} / {{rows.length}}</text><text class="growth-muted">完成状态由你主动确认，不代表观看时长或能力评定。</text><view v-for="r in rows" :key="r.id" class="lesson-choice" role="button" tabindex="0" :aria-pressed="selected===r.id" :class="{selected:selected===r.id}" @click="selected=r.id" @keydown.enter.prevent="selected=r.id" @keydown.space.prevent="selected=r.id"><text>{{r.chapter}} · {{r.position}}. {{r.title}}</text><text class="growth-muted">{{Number(r.completed)===1?'已完成':'待学习'}}</text></view><text v-if="!rows.length" class="growth-muted">带领者尚未发布课节。</text></view><view v-if="active" class="growth-card"><text class="growth-card-title">{{active.title}}</text><text class="lesson-content">{{active.content}}</text><AccessibleButton v-if="active.project_id" class="growth-button secondary" @click="practice">查看手作步骤与教学视频</AccessibleButton><AccessibleButton class="growth-button" :disabled="saving" @click="mark">{{saving?'正在保存…':Number(active.completed)===1?'改为继续学习':'我已完成本课节'}}</AccessibleButton></view></template><text class="growth-foot">本系统不能替代专业心理咨询</text></view></template>
<style scoped>.lesson-choice{padding:24rpx 20rpx;margin-top:16rpx;display:flex;flex-direction:column;gap:10rpx;border:1rpx solid var(--zy-border,#e1e9e3);border-radius:16rpx}.lesson-choice.selected{border-color:var(--zy-brand-600, #217e76);background:#edf5ef}.lesson-content{display:block;white-space:pre-wrap;line-height:1.9;font-size:var(--zy-text-body, 28rpx);margin:24rpx 0}</style>

<style scoped>
.lesson-choice:focus-visible { outline: 3px solid var(--zy-primary, var(--zy-brand-600, #217e76)); outline-offset: 3px; }
</style>
