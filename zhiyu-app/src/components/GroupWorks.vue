<script setup>
import AccessibleButton from './AccessibleButton.vue'
import {ref,onMounted} from 'vue'
import {request} from '../utils/request'
import {API_BASE_URL} from '../utils/config'
const props=defineProps({groupId:String});const rows=ref([]),busy=ref(false),error=ref(''),more=ref(false)
async function load(append=false){if(busy.value)return;busy.value=true;error.value='';try{const list=(await request({url:'/api/groups/'+props.groupId+'/works',data:append?{before:rows.value.at(-1)?.id}:{}}))||[];rows.value=append?[...rows.value,...list]:list;more.value=list.length===20}catch(e){error.value='组内作品暂时没有读取成功'}finally{busy.value=false}}
function open(id){uni.navigateTo({url:'/pages/work/detail?id='+id})}
function publish(){uni.navigateTo({url:'/pages/work/publish?groupId='+props.groupId})}
function image(url){return url?.startsWith('/')?API_BASE_URL+url:url}
onMounted(()=>load())
</script>
<template><view class="growth-card"><view class="growth-row growth-spread"><text class="growth-card-title">组内作品墙</text><AccessibleButton size="mini" @click="publish">分享作品</AccessibleButton></view><text class="growth-muted">仅本小组成员可见，退出后将失去访问权限。</text><view v-if="error" class="growth-error">{{error}}</view><view class="group-work-grid"><AccessibleButton v-for="w in rows" :key="w.id" class="group-work" @click="open(w.id)"><image v-if="w.coverUrl" :src="image(w.coverUrl)" mode="aspectFill"/><text>{{w.title}}</text><text class="growth-muted">{{w.mediaType==='video'?'视频作品':w.mediaType==='text'?'创作故事':'手作作品'}}</text></AccessibleButton></view><text v-if="!rows.length&&!busy" class="growth-muted">作品墙还是空的，分享一份创作给同行的朋友吧。</text><AccessibleButton :disabled="busy" @click="load(more)">{{busy?'加载中…':more?'加载更多':'刷新作品'}}</AccessibleButton></view></template>
<style scoped>.group-work-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:16rpx;margin:24rpx 0}.group-work{padding:0!important;background:#f5f7f2!important;border:1px solid #dfe7dc!important;border-radius:18rpx;overflow:hidden;text-align:left;width:100%}.group-work image{width:100%;height:220rpx}.group-work text{display:block;padding:12rpx 20rpx;line-height:1.5;font-size:26rpx}button{font-size:25rpx}</style>
