<template>
	<view class="growth-page programs-page" :class="[{'motion-reduced':reducedMotion}, zyThemeClass]">
 <view class="program-hero"><view class="hero-copy"><text class="growth-eyebrow">一起创作 · 让陪伴发生</text><text class="growth-title">为自己留一段时间</text><text class="growth-subtitle">从一次手作开始，找到适合你的节奏。</text><view class="hero-pills"><text>真实相聚</text><text>自主选择</text><text>温和陪伴</text></view></view><XiaotuanPet :size="88" face="calm" class="companion" aria-hidden="true"/></view>
 <view v-if="upcoming.length&&!loading" class="next-plan"><view><text class="section-kicker">下一段安排 · {{upcoming.length}} 项待参加</text><text class="plan-title">{{upcoming[0].title}}</text><text class="growth-muted">{{format(upcoming[0].start_time)}} · {{status[upcoming[0].myStatus]}}</text></view><AccessibleButton class="text-button" @click="filter='MINE';query=''">查看预约</AccessibleButton></view>
 <AccessibleButton class="growth-button secondary" @click="goConversations">我的预约交流</AccessibleButton><view class="catalog-head"><view><text class="section-kicker">EXPLORE TOGETHER</text><text class="section-title">发现适合你的体验</text></view><AccessibleButton class="text-button" :disabled="loading||!!busy" @click="load">{{loading?'更新中…':'刷新'}}</AccessibleButton></view>
 <view class="search-box"><text class="search-label">搜索</text><input v-model="query" placeholder="活动名称或地点" confirm-type="search" aria-label="搜索活动名称或地点"/><AccessibleButton v-if="query" class="text-button" @click="query=''">清除</AccessibleButton></view>
 <scroll-view scroll-x class="filter-scroll"><view class="filters"><AccessibleButton v-for="f in filters" :key="f.id" :class="['filter-button',{active:filter===f.id}]" :aria-pressed="filter===f.id" @click="filter=f.id">{{f.text}}</AccessibleButton></view></scroll-view>
 <StateBlock v-if="error" type="error" title="活动暂时没有载入" :description="error" action-text="重新加载" :action-loading="loading" @action="load"/>
 <StateBlock v-else-if="loading" type="loading" title="正在读取活动安排…"/>
 <StateBlock v-else-if="!visible.length" type="empty" face="calm" :title="emptyTitle" :description="emptyDescription" :action-text="emptyActionText" @action="onEmptyAction"/>
 <template v-else><text class="result-count">{{visible.length}} 项体验 · 时间均为北京时间</text><view class="program-grid"><view v-for="p in visible" :key="p.id" class="program-card">
 <view class="cover-wrap"><image :src="covers[p.kind]||covers.WORKSHOP" mode="aspectFill" class="program-cover" lazy-load :alt="(p.title||'活动')+'封面'"/><text class="cover-label">{{names[p.kind]}} · 材料示意图</text><view class="date-chip"><text>{{p.start_time.slice(5,7)}} 月</text><text class="date-day">{{p.start_time.slice(8,10)}}</text></view></view>
 <view class="card-content"><view class="card-state" :class="{pending:p.myStatus==='PENDING',muted:p.status==='CANCELLED'||p.myStatus==='REJECTED'}"><text class="state-dot"/>{{p.status==='CANCELLED'?'活动已取消':status[p.myStatus]||(ended(p)?'报名已结束':'开放报名')}}</view><text class="program-title">{{p.title}}</text><text class="meta">时间　{{format(p.start_time)}}</text><text class="meta">地点　{{p.location}}</text><view class="capacity-row"><text>{{p.booked}} / {{p.capacity}} 个名额</text><text>{{Math.max(0,p.capacity-p.booked)}} 个剩余</text></view><view class="growth-progress"><view class="growth-progress-fill" :style="{width:Math.min(100,p.booked/p.capacity*100)+'%'}"/></view>
 <AccessibleButton class="details-toggle" :aria-expanded="expanded===p.id" @click="expanded=expanded===p.id?'':p.id">{{expanded===p.id?'收起安排 −':'查看内容与准备事项 ＋'}}</AccessibleButton><view v-if="expanded===p.id" class="details"><text>{{p.description}}</text><text>结束时间：{{format(p.end_time)}}</text><text v-if="p.co_leader">协同带领：{{p.co_leader}}</text><text>{{p.materials?'准备材料：'+p.materials:'材料安排请以主办人通知为准。'}}</text><text v-if="p.kind==='GUIDANCE'">提交预约后等待带领者确认；未通过的预约不占用名额。</text></view>
 <view class="card-actions"><button v-if="p.status==='PUBLISHED'" class="text-button" open-type="share" @click="shareProgram(p)">分享这项体验</button><AccessibleButton v-if="p.myStatus==='CHECKED_IN'&&finished(p)" class="growth-button secondary" @click="feedback(p)">记录 / 查看体验反馈</AccessibleButton><AccessibleButton v-if="p.kind==='COURSE'&&p.status==='PUBLISHED'&&['BOOKED','CHECKED_IN'].includes(p.myStatus)" class="growth-button" @click="openCourse(p)">进入课程</AccessibleButton><AccessibleButton v-if="['PENDING','BOOKED'].includes(p.myStatus)" class="growth-button secondary" :disabled="!!busy" @click="act(p,true)">{{busy===p.id?'处理中…':'取消预约'}}</AccessibleButton><AccessibleButton v-else-if="p.myStatus!=='CHECKED_IN'&&p.status==='PUBLISHED'" class="growth-button" :disabled="!!busy||p.booked>=p.capacity||ended(p)" @click="act(p,false)">{{busy===p.id?'提交中…':ended(p)?'报名已结束':p.booked>=p.capacity?'名额已满':p.kind==='GUIDANCE'?'申请预约':'预约参加'}}</AccessibleButton></view></view>
 </view></view></template><text class="growth-foot">按自己的节奏参与，不必与他人比较。
本系统不能替代专业心理咨询</text><ActionSheet :open="!!pending" :title="pendingTitle" :description="pendingDescription" :actions="pendingActions" cancel-text="再想想" :busy="!!busy" @select="confirmAct" @close="pending=null"/><Toast/>
</view></template>
<script setup>
import {useInteraction} from '@/composables/useInteraction'
import StateBlock from '@/components/StateBlock.vue'
import ActionSheet from '@/components/ActionSheet.vue'
import Toast from '@/components/Toast.vue'
import XiaotuanPet from '@/components/XiaotuanPet.vue'
import AccessibleButton from '@/components/AccessibleButton.vue'
import {useToast} from '@/composables/useToast'
const {reducedMotion}=useInteraction()
const toast=useToast()

import {useContentShare} from '@/composables/useContentShare'
import {ref,computed} from 'vue';import {onLoad,onShow} from '@dcloudio/uni-app';import {request} from '@/utils/request'
const shareTarget=ref(null),incoming=ref('');const {share}=useContentShare('program',()=>shareTarget.value);
function shareProgram(p){shareTarget.value=p;share()}
function goConversations(){uni.navigateTo({url:'/sub-programs/pages/programs/conversations'})}
onLoad(q=>{if(/^[1-9]\d{0,18}$/.test(q.id||''))incoming.value=q.id})
const rows=ref([]),loading=ref(false),busy=ref(''),error=ref(''),filter=ref('ALL'),query=ref(''),expanded=ref('');const names={WORKSHOP:'主题工作坊',COURSE:'系列课程',GUIDANCE:'一对一指导'},status={PENDING:'等待带领者确认',REJECTED:'本次预约未通过',BOOKED:'已预约',CHECKED_IN:'已签到',CANCELLED:'已取消预约'};const filters=[{id:'ALL',text:'全部'},{id:'WORKSHOP',text:'工作坊'},{id:'COURSE',text:'课程'},{id:'GUIDANCE',text:'一对一'},{id:'MINE',text:'我的预约'}];const visible=computed(()=>rows.value.filter(p=>(filter.value==='ALL'||(filter.value==='MINE'?!!p.myStatus:p.kind===filter.value))&&[p.title,p.location].join(' ').toLowerCase().includes(query.value.trim().toLowerCase())).sort((a,b)=>Number(ended(a))-Number(ended(b))||new Date(a.start_time)-new Date(b.start_time)))
const upcoming=computed(()=>rows.value.filter(p=>['PENDING','BOOKED'].includes(p.myStatus)&&p.status==='PUBLISHED'&&!ended(p)).sort((a,b)=>a.start_time.localeCompare(b.start_time)))
const covers={WORKSHOP:'/static/library/clay.jpg',COURSE:'/static/library/yarn.jpg',GUIDANCE:'/static/library/paper.jpg'}
function reset(){query.value='';filter.value='ALL'}
const emptyTitle=computed(()=>query.value?'没有找到匹配的体验':filter.value==='MINE'?'还没有预约记录':'新的体验正在准备中')
const emptyDescription=computed(()=>query.value?'换个关键词，或清除筛选再试试。':filter.value==='MINE'?'从一场轻松的工作坊开始，为自己留一点时间。':'主办人发布后会出现在这里，稍后再来看看。')
const emptyActionText=computed(()=>(query.value||filter.value!=='ALL')?'浏览全部体验':'刷新看看')
function onEmptyAction(){if(query.value||filter.value!=='ALL')reset();else load()}

function feedback(p){uni.navigateTo({url:'/sub-programs/pages/programs/feedback?id='+p.id})}
function finished(p){return new Date(p.end_time+'+08:00').getTime()<=Date.now()}
function openCourse(p){uni.navigateTo({url:'/sub-programs/pages/programs/course?id='+p.id})}
function format(s){return String(s||'').replace('T',' ').slice(0,16)}function ended(p){return new Date(p.start_time+'+08:00').getTime()<=Date.now()}
async function load(){loading.value=true;error.value='';try{rows.value=await request({url:'/api/growth/programs'});if(incoming.value){const target=rows.value.find(p=>p.id===incoming.value);if(target){query.value=target.title;expanded.value=target.id}else error.value='分享的活动已取消或不可访问';incoming.value=''}}catch(e){rows.value=[];error.value=e.message||'暂时无法读取活动，请重试'}finally{loading.value=false}}
const pending=ref(null)
const pendingTitle=computed(()=>pending.value&&pending.value.cancel?'取消预约':'确认预约')
const pendingDescription=computed(()=>{const a=pending.value;if(!a)return'';return a.cancel?'取消后名额将释放。':a.p.title+' · '+format(a.p.start_time)+' · '+a.p.location+(a.p.kind==='GUIDANCE'?'。提交后需等待带领者确认。':'。开始前可取消。')})
const pendingActions=computed(()=>{const a=pending.value;if(!a)return[];return[{key:'confirm',label:a.cancel?'确认取消':'确认预约',tone:a.cancel?'danger':'primary',busy:!!busy.value}]})
function act(p,cancel){if(busy.value)return;pending.value={p,cancel}}
async function confirmAct(){const a=pending.value;if(!a||busy.value)return;busy.value=a.p.id;try{await request({url:'/api/growth/programs/'+a.p.id+'/book',method:a.cancel?'DELETE':'POST'});pending.value=null;await load();toast.success(a.cancel?'已取消':a.p.kind==='GUIDANCE'?'已提交，等待确认':'预约成功')}catch(e){error.value=e.message}finally{busy.value=''}}
onShow(load)
</script>
<style scoped src="./programs.css"></style>
