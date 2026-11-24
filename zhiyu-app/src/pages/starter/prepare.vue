<template>
	<view class="growth-page" :class="[{'motion-reduced':reducedMotion}, zyThemeClass]">
 <StateBlock v-if="loading" type="loading" shape="list" title="正在读取准备清单…" />
 <view v-else-if="error" class="prepare-error">
  <StateBlock type="error" title="这件练习暂时没准备好" :description="error" action-text="重新读取" @action="load" />
  <AccessibleButton class="state-back" @click="back">回到手作起点</AccessibleButton>
 </view>
 <template v-else-if="item">
  <ZyPageHead seal="备" :title="item.title" :subtitle="`约 ${item.minutes} 分钟 · ${item.steps.length} 步 · 可随时暂停`" />
  <StarterDiagram :kind="item.diagram"/><text class="growth-muted">原创纹样示意，具体操作以步骤文字为准。</text>
  <view class="growth-card"><text class="growth-card-title">手边准备好了 {{checked.length}} / {{item.materials.length}}</text><AccessibleButton v-for="(m,i) in item.materials" :key="m.name" class="material-row" :aria-pressed="checked.includes(i)" @click="toggle(i)"><text class="check">{{checked.includes(i)?'✓':'○'}}</text><view><text class="material-title">{{m.name}} · {{m.qty}}</text><text class="growth-muted">替代方案：{{m.substitute}}</text></view></AccessibleButton><text class="growth-muted">清单仅在当前页面保留，不上传。</text></view>
  <view class="growth-card safety-card"><text class="growth-card-title">开工前看一眼</text><text class="growth-muted">{{item.hazards}}</text><text class="growth-muted">{{item.guidance}}</text><text class="growth-muted">以上为编辑风险提示，不代表专业安全认证。</text></view>
  <view class="growth-card"><text class="growth-card-title">四步就能完成</text><view v-for="(s,i) in item.steps" :key="s.title" class="step-preview"><text>{{i+1}}. {{s.title}}</text><text class="growth-muted">{{s.guide}}</text><text class="rescue">遇到困难：{{s.rescue}}</text></view></view>
  <view class="growth-card"><text class="growth-card-title">内容从哪里来</text><text class="growth-muted">{{item.authorship}}</text><text class="growth-muted">{{item.source.label}}</text><AccessibleButton v-if="item.source.url" class="growth-button secondary" @click="copySource">复制背景参考链接</AccessibleButton><text class="growth-muted">整理日期：2026-10-11 · 版本 {{item.version}}</text></view>
  <AccessibleButton class="growth-button" :disabled="checked.length!==item.materials.length" @click="start">{{checked.length===item.materials.length?'材料就绪，开始创作 →':'勾选材料或替代品后开始'}}</AccessibleButton>
  <AccessibleButton class="growth-button secondary" @click="back">换一件更适合的练习</AccessibleButton>
 </template></view></template>
<script setup>
import ZyPageHead from '../../components/ZyPageHead.vue'
import{ref}from'vue';import{onLoad}from'@dcloudio/uni-app';import{http}from'../../utils/request';import{useInteraction}from'../../composables/useInteraction';import StarterDiagram from '../../components/StarterDiagram.vue';import AccessibleButton from '../../components/AccessibleButton.vue';import StateBlock from '../../components/StateBlock.vue'
const{reducedMotion}=useInteraction(),item=ref(null),loading=ref(false),error=ref(''),checked=ref([]);let id=''
onLoad(q=>{id=q.id;load()})
async function load(){loading.value=true;error.value='';try{const all=await http.get('/api/craft/starter');item.value=all.find(x=>x.id===id);if(!item.value)error.value='该练习已调整或暂未上架，请返回目录选择其他项目。'}catch(e){error.value=e.message||'准备清单加载失败'}finally{loading.value=false}}
function toggle(i){checked.value=checked.value.includes(i)?checked.value.filter(n=>n!==i):[...checked.value,i]}
function start(){if(checked.value.length!==item.value.materials.length)return;uni.navigateTo({url:'/pages/craft/session?projectId='+id})}
function back(){uni.navigateBack()}
function copySource(){uni.setClipboardData({data:item.value.source.url})}
</script>

<style scoped>.prepare-error{display:flex;flex-direction:column;align-items:center;padding-top:80rpx}.state-back{background:transparent;border:0;border-bottom:1rpx solid var(--zy-border,#d3ded8);border-radius: var(--zy-radius-control, 18rpx);color:var(--zy-muted,#6e7b75);font-size:var(--zy-text-caption,24rpx);min-height:72rpx;margin-top:6rpx;padding:0 8rpx}.state-back::after{border:0}.growth-muted{display:block;margin-top:8rpx}.material-row{display:flex;gap:20rpx;padding:24rpx 0;background:transparent;text-align:left;line-height:1.8;border-bottom:1rpx solid var(--zy-border,#d3ded8)}.check{color:var(--zy-brand-700,#183369);font-size:var(--zy-text-title,32rpx)}.material-title{font-family:var(--zy-font-display,serif);font-size:var(--zy-text-body,28rpx);font-weight:500;color:var(--zy-title,#111816)}.step-preview{padding:22rpx 0;border-bottom:1rpx solid var(--zy-border,#d3ded8);font-size:var(--zy-text-body,28rpx)}.rescue{display:block;color:var(--zy-text,#42524c);background:var(--zy-bg-sunken,#e9efeb);border-left:2rpx solid var(--zy-care,#a64842);border-radius: var(--zy-radius-control, 18rpx);padding:14rpx 16rpx;font-size:var(--zy-text-caption,24rpx);line-height:1.8;margin-top:12rpx}.safety-card{border-left:2rpx solid var(--zy-care,#a64842)}button::after{border:0}</style>
