<template>
 <view class="growth-page" :class="{'motion-reduced':reducedMotion}">
  <view class="growth-hero"><text class="growth-eyebrow">FIRST MAKING · 手作起点</text><text class="growth-title">用手边的材料，做一件小作品</text><text class="growth-subtitle">选好时间和场所，小织陪你从准备走到完成。</text></view>
  <view v-if="active.length" class="growth-card resume-card"><text class="growth-card-title">接着上次的进度</text><button v-for="s in active" :key="s.sessionId" class="resume-row" @click="resume(s)"><text>{{s.projectTitle}}</text><text class="growth-muted">第 {{s.currentStep}} / {{s.totalSteps}} 步 · 继续 →</text></button></view>
  <view v-if="resumeError" class="growth-error">{{resumeError}}<button class="growth-button secondary" @click="loadActive">重新读取进度</button></view>
  <view class="growth-card filters"><text class="growth-card-title">今天的创作条件</text><text class="growth-muted">时间为编辑估计，可随时暂停。选择只用于本页筛选。</text>
   <text class="filter-label">我有多少时间</text><view class="growth-row"><button v-for="n in [10,15,20]" :key="n" class="growth-choice" :class="{selected:options.minutes===n}" :aria-pressed="options.minutes===n" @click="options.minutes=n">{{n}} 分钟内</button></view>
   <text class="filter-label">手边的主要材料</text><view class="growth-row"><button v-for="m in ['全部','纸','线']" :key="m" class="growth-choice" :class="{selected:options.medium===m}" :aria-pressed="options.medium===m" @click="options.medium=m">{{m}}</button></view>
   <text class="filter-label">我在哪里</text><view class="growth-row"><button v-for="p in ['宿舍桌面','图书馆','活动室']" :key="p" class="growth-choice" :class="{selected:options.place===p}" :aria-pressed="options.place===p" @click="options.place=p">{{p}}</button></view>
   <button class="tool-toggle" :aria-pressed="options.noScissors" @click="options.noScissors=!options.noScissors">{{options.noScissors?'✓':'○'}} 只看不需要剪刀的项目</button>
  </view>
  <view v-if="loading" class="growth-card growth-skeleton">正在准备练习目录…</view>
  <view v-else-if="error" class="growth-error">{{error}}<button class="growth-button" @click="load">重试</button></view>
  <template v-else>
   <view v-if="featured" class="growth-card featured"><text class="growth-eyebrow">为此刻选一件 · {{matches.length}} 个匹配</text><CraftCover :category="featured.medium"/><StarterDiagram :kind="featured.diagram"/><text class="growth-card-title">{{featured.title}}</text><text class="growth-muted">{{starterReason(featured,options)}}</text><text class="growth-muted">{{featured.materials.map(m=>m.name).join(' / ')}}</text><button class="growth-button" @click="open(featured)">看看准备清单 →</button><button v-if="matches.length>1" class="growth-button secondary" @click="choice=(choice+1)%matches.length">换一个</button></view>
   <view v-else class="growth-card"><text class="growth-card-title">暂时没有符合条件的练习</text><text class="growth-muted">试试增加时间或更换材料；不会自动放宽你的工具限制。</text><button class="growth-button secondary" @click="reset">重置筛选</button></view>
   <view v-if="matches.length>1" class="growth-grid"><button v-for="item in matches.filter(x=>x.id!==featured.id)" :key="item.id" class="growth-card result" @click="open(item)" ><CraftCover :category="item.medium"/><text class="growth-card-title">{{item.title}}</text><text class="growth-muted">{{item.minutes}} 分钟 · {{item.medium}} · {{item.steps.length}} 步</text><text class="growth-muted">{{item.tools.length?item.tools.join(' / '):'无需剪刀与胶带'}}</text></button></view>
   <text class="growth-muted">原创入门练习，不代表传统技艺认证或专业疗效。具体来源见准备页。</text>
  </template>
  <button class="growth-button secondary" @click="openCredits()">图片来源与授权 ↗</button><text class="growth-foot">本系统不能替代专业心理咨询</text>
 </view>
</template>
<script setup>
function openChat(){uni.navigateTo({url:'/pages/emotion/chat'})}
function openMusic(){uni.navigateTo({url:'/pages/music/index'})}
function openCredits(){uni.navigateTo({url:'/pages/music/credits'})}
import CraftCover from "../../components/CraftCover.vue"
import {ref,reactive,computed,watch} from 'vue'
import {onShow} from '@dcloudio/uni-app'
import {http} from '../../utils/request'
import {useInteraction} from '../../composables/useInteraction'
import {filterStarter,starterReason} from '../../utils/starter'
import StarterDiagram from '../../components/StarterDiagram.vue'
const {reducedMotion}=useInteraction(),items=ref([]),active=ref([]),loading=ref(false),error=ref(''),resumeError=ref(''),choice=ref(0)
const options=reactive({minutes:20,medium:'全部',place:'宿舍桌面',noScissors:false})
const matches=computed(()=>filterStarter(items.value,options)),featured=computed(()=>matches.value[choice.value%matches.value.length])
watch(options,()=>choice.value=0)
async function load(){if(loading.value)return;loading.value=true;error.value='';try{items.value=await http.get('/api/craft/starter')}catch(e){error.value=e.message||'练习目录暂时无法读取'}finally{loading.value=false}}
async function loadActive(){resumeError.value='';try{active.value=await http.get('/api/craft/session/active')}catch{active.value=[];resumeError.value='暂时无法读取上次进度，可重试。'}}
onShow(()=>{load();loadActive()})
function reset(){Object.assign(options,{minutes:20,medium:'全部',place:'宿舍桌面',noScissors:false})}
function open(item){uni.navigateTo({url:'/pages/starter/prepare?id='+item.id})}
function resume(s){uni.navigateTo({url:`/pages/craft/session?projectId=${s.projectId}&sessionId=${s.sessionId}`})}
</script>
<style src="../../styles/growth.css"></style>
<style scoped>.growth-page .filters button.growth-choice{width:auto;flex:1;padding:14rpx 10rpx;margin:0;text-align:center;font-size:24rpx}.growth-muted{display:block}.filter-label{display:block;margin:28rpx 0 14rpx;font-size:25rpx;font-weight:600}.growth-row{flex-wrap:wrap}.tool-toggle{margin:26rpx 0 0;text-align:left;background:#edf5f2;color:#267a6c;font-size:26rpx;border-radius:14rpx}.featured{border-color:#b9d8cb}.result{text-align:left;width:100%;margin:0;line-height:1.7}.resume-row{display:flex;justify-content:space-between;gap:18rpx;flex-wrap:wrap;text-align:left;padding:20rpx 0;border-bottom:1rpx solid #dfe8e5;background:transparent;font-size:26rpx}.resume-card{border-left:6rpx solid #dbb370}button::after{border:0}</style>
