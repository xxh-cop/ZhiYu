<script setup>
import AccessibleButton from '../../components/AccessibleButton.vue'
import {ref} from 'vue';import {onShow,onUnload} from '@dcloudio/uni-app';import {request} from '../../utils/request';import {API_BASE_URL} from '../../utils/config';
const rows=ref([]),busy=ref(false),saving=ref(''),error=ref(''),more=ref(false);let active=true;
async function load(append=false){if(busy.value)return;busy.value=true;error.value='';try{const list=await request({url:'/api/media/mine',data:append?{before:rows.value.at(-1)?.id}:{}});if(active){rows.value=append?[...rows.value,...list]:list;more.value=list.length===50}}catch(e){error.value=e.message}finally{busy.value=false}}
async function save(item){if(saving.value)return;saving.value=item.id;error.value='';try{const data=await request({url:'/api/media/'+item.id+'/playback'});const url=API_BASE_URL+data.url;
// #ifdef H5
const res=await fetch(url);if(!res.ok)throw Error('文件已失效，请刷新重试');const blob=await res.blob();if(!active)return;const object=URL.createObjectURL(blob),link=document.createElement('a');link.href=object;link.download='zhiyu-'+item.id+(item.kind==='video'?'.mp4':blob.type==='image/png'?'.png':blob.type==='image/webp'?'.webp':'.jpg');document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(object),30000);
// #endif
// #ifdef MP-WEIXIN
const downloaded=await new Promise((resolve,reject)=>uni.downloadFile({url,success:r=>r.statusCode===200?resolve(r):reject(Error('文件读取失败')),fail:reject}));if(!active)return;await new Promise((resolve,reject)=>(item.kind==='video'?uni.saveVideoToPhotosAlbum:uni.saveImageToPhotosAlbum)({filePath:downloaded.tempFilePath,success:resolve,fail:reject}));
// #endif
uni.showToast({title:'已发起保存',icon:'none'});
}catch(e){error.value=e.message||'保存失败，请检查网络和相册权限后重试'}finally{saving.value=''}}
onShow(()=>load());onUnload(()=>{active=false})
</script>
<template><view class="growth-page"><view class="growth-hero"><text class="growth-eyebrow">个人资料 · 媒体副本</text><text class="growth-title">保存自己的创作</text><text class="growth-subtitle">这里仅展示本人上传的受保护图片和视频。下载后的副本由你自行保管。</text></view><view v-if="error" class="growth-error" role="alert">{{error}}<AccessibleButton :disabled="busy" @click="load()">重新读取</AccessibleButton></view><view v-if="!rows.length&&!busy&&!error" class="growth-card"><text class="growth-card-title">还没有可保存的媒体</text><text class="growth-muted">新上传的作品图片与视频会出现在这里。早期公开图片请从原作品页保存。</text></view><view v-for="m in rows" :key="m.id" class="growth-card media-row"><view><text class="growth-card-title">{{m.kind==='video'?'原创视频':'创作图片'}}</text><text class="growth-muted">{{(m.size_bytes/1048576).toFixed(2)}} MB · {{String(m.create_time).slice(0,10)}}</text></view><AccessibleButton class="growth-button" :disabled="!!saving" @click="save(m)">{{saving===m.id?'准备文件…':'保存原文件'}}</AccessibleButton></view><AccessibleButton v-if="more" :disabled="busy" @click="load(true)">加载更多</AccessibleButton><text class="growth-foot">本系统不能替代专业心理咨询</text></view></template>
<style src="../../styles/growth.css"></style><style scoped>.media-row{display:flex;justify-content:space-between;align-items:center;gap:20rpx}.media-row button{flex-shrink:0;margin:0;font-size:26rpx}</style>
