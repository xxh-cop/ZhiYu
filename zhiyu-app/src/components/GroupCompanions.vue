<script setup>
import AccessibleButton from './AccessibleButton.vue'
import {ref,onMounted,computed} from 'vue'
import {request} from '../utils/request'
const props=defineProps({groupId:String,members:Array,myMemberId:String})
const candidates=computed(()=>(props.members||[]).filter(m=>m.id!==props.myMemberId))
const rows=ref([]),error=ref(''),busy=ref(false)
const labels={PENDING:'等待回应',ACCEPTED:'已结伴',DECLINED:'已婉拒',CANCELLED:'已取消'}
async function load(){error.value='';try{rows.value=(await request({url:'/api/groups/'+props.groupId+'/companions'}))||[]}catch(e){error.value='结伴状态暂时没有读取成功'}}
async function run(url,method,data){if(busy.value)return;busy.value=true;error.value='';try{await request({url:'/api/groups/'+props.groupId+'/companions'+url,method,data});await load()}catch(e){error.value=e.message}finally{busy.value=false}}
function invite(){const available=candidates.value;if(!available.length)return;uni.showActionSheet({itemList:available.map(m=>m.nickname),success:r=>run('','POST',{memberId:available[r.tapIndex].id})})}
onMounted(load)
</script>
<template><view class="growth-card"><view class="growth-row growth-spread"><text class="growth-card-title">创作搭子</text><AccessibleButton variant="secondary" class="action-btn" :disabled="busy||!candidates.length" @click="invite">邀请结伴</AccessibleButton></view><text class="growth-muted">邀请需对方同意，仅双方能看到结伴状态，可随时取消。</text><text v-if="error" class="growth-error" role="alert">{{error}}</text><view v-for="r in rows" :key="r.id" class="companion-row"><text>{{r.nickname}} · {{labels[r.state]}}</text><view class="growth-row"><template v-if="r.state==='PENDING'&&r.incoming"><AccessibleButton variant="ghost" size="sm" :disabled="busy" @click="run('/'+r.id,'PUT',{action:'ACCEPT'})">接受</AccessibleButton><AccessibleButton variant="ghost" size="sm" :disabled="busy" @click="run('/'+r.id,'PUT',{action:'DECLINE'})">婉拒</AccessibleButton></template><AccessibleButton v-if="['PENDING','ACCEPTED'].includes(r.state)" variant="ghost" size="sm" :disabled="busy" @click="run('/'+r.id,'PUT',{action:'CANCEL'})">取消</AccessibleButton></view></view><text v-if="!rows.length" class="growth-muted">还没有结伴记录。可以邀请一位组员一起创作。</text><AccessibleButton variant="ghost" class="action-btn" :disabled="busy" @click="load">刷新状态</AccessibleButton></view></template>
<style scoped>.companion-row{padding:24rpx 0;border-bottom:1px solid var(--zy-border);display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:12rpx}.action-btn{min-height:72rpx;padding:0 24rpx;margin:16rpx 0 0;font-size:var(--zy-text-caption, 24rpx)}.companion-row button{margin:0;font-size:var(--zy-text-caption, 24rpx)}</style>
