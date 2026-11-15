<template>
	<view class="growth-page" :class="[{'motion-reduced':reducedMotion}, zyThemeClass]">
 <view class="growth-hero"><text class="growth-eyebrow">TRACE · 平面临摹辅助</text><text class="growth-title">让纹样，落在纸上</text><text class="growth-subtitle">固定手机，调整线稿位置，再观察纸面临摹。图案不会自动锚定真实空间。</text></view>
 <view class="tracing-frame" :style="{height:size+'px'}" @touchstart="touchStart" @touchmove.stop.prevent="touchMove">
  <image v-if="photo" :src="photo" class="camera-surface" mode="aspectFill" alt="你拍摄的照片"/>
  <!-- #ifdef H5 -->
  <view :id="hostId" class="camera-surface"/>
  <!-- #endif -->
  <!-- #ifdef MP-WEIXIN -->
  <camera v-if="active" class="camera-surface" device-position="back" flash="off" @initdone="busy=false" @error="cameraError" @stop="stop"/>
  <!-- #endif -->
  <text v-if="!active&&!photo" class="frame-hint">可以先在空白画布调整线稿</text>
  <image v-if="overlay" :src="overlay" class="trace-overlay" :style="overlayStyle" mode="aspectFit" alt="描红线稿"/>
  <view class="frame-badge">{{active?'相机已开启 · 不录制':photo?'本地图片 · 不上传':'空白画布'}} · {{locked?'位置已锁定':'拖动调整位置'}}</view>
 </view>
 <canvas canvas-id="trace-source" class="source-canvas" :style="{width:size+'px',height:size+'px'}"/>
 <view v-if="error" class="growth-error" role="alert">{{error}}</view>
 <view class="growth-row"><AccessibleButton class="growth-button" @click="active||busy?stop():start()">{{busy?'取消开启':active?'关闭相机':'开启相机'}}</AccessibleButton><AccessibleButton class="growth-button secondary" :disabled="busy" @click="choosePhoto">使用本地图片</AccessibleButton></view>
 <view class="growth-card controls"><view class="growth-row growth-spread"><text class="growth-card-title">调整线稿</text><AccessibleButton class="lock" @click="locked=!locked">{{locked?'解锁位置':'锁定位置'}}</AccessibleButton></view><text class="growth-muted">透明度 {{opacity}}%</text><slider :value="opacity" :min="10" :max="100" activeColor="#217e76" @changing="opacity=$event.detail.value" @change="opacity=$event.detail.value"/><text class="growth-muted">大小 {{scale}}%</text><slider :value="scale" :min="40" :max="160" :disabled="locked" activeColor="#217e76" @changing="scale=$event.detail.value" @change="scale=$event.detail.value"/><text class="growth-muted">旋转 {{angle}}°</text><slider :value="angle" :min="-180" :max="180" :disabled="locked" activeColor="#217e76" @changing="angle=$event.detail.value" @change="angle=$event.detail.value"/><AccessibleButton class="growth-button secondary" @click="reset">恢复位置与大小</AccessibleButton></view>
 <text class="growth-foot">只在你点击后申请相机权限，不录音、不录制、不上传画面；离开页面自动关闭。<br/>本系统不能替代专业心理咨询</text>
</view></template>
<script setup>
import {useInteraction} from '../../composables/useInteraction'
const {reducedMotion}=useInteraction()

import{ref,computed,getCurrentInstance,nextTick}from'vue'
import{onLoad,onReady,onHide,onUnload}from'@dcloudio/uni-app'
import{makeShapes,normalizePlan,drawShapes}from'../../utils/lineart'
import AccessibleButton from '../../components/AccessibleButton.vue'
const instance=getCurrentInstance(),size=Math.min(uni.getSystemInfoSync().windowWidth-28,650),hostId='trace-camera-'+instance.uid
const active=ref(false),busy=ref(false),error=ref(''),photo=ref(''),overlay=ref(''),locked=ref(false),opacity=ref(65),scale=ref(90),angle=ref(0),x=ref(0),y=ref(0)
const overlayStyle=computed(()=>({opacity:opacity.value/100,transform:`translate(-50%,-50%) translate(${x.value}px,${y.value}px) rotate(${angle.value}deg) scale(${scale.value/100})`}))
let plan=normalizePlan(),stream=null,video=null,generation=0,alive=true,touch=null
onLoad(q=>{plan=normalizePlan({motif:q.motif,petals:Number(q.petals),rings:Number(q.rings)})})
function stop(){generation++;active.value=false;busy.value=false;if(stream){stream.getTracks().forEach(t=>t.stop());stream=null}if(video){video.srcObject=null;video.remove();video=null}}
async function start(){if(busy.value||active.value)return;error.value='';photo.value='';busy.value=true;const current=++generation
 // #ifdef H5
 try{if(!navigator.mediaDevices?.getUserMedia)throw new Error('需要 HTTPS 或本机地址才能开启相机。你也可以使用本地图片。');const acquired=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'}},audio:false});if(!alive||current!==generation){acquired.getTracks().forEach(t=>t.stop());return}stream=acquired;await nextTick();const host=document.getElementById(hostId);if(!host)throw new Error('画面容器暂不可用，请重试');video=document.createElement('video');video.muted=true;video.autoplay=true;video.playsInline=true;video.setAttribute('playsinline','');video.style.cssText='width:100%;height:100%;object-fit:cover;';video.srcObject=stream;host.appendChild(video);await video.play();if(current!==generation)return;active.value=true;busy.value=false}catch(e){if(current!==generation)return;stop();error.value=e.name==='NotAllowedError'?'相机权限未开启。可在浏览器设置中允许，或使用本地图片。':e.name==='NotFoundError'?'未找到相机，可使用本地图片继续。':e.message||'相机暂时无法开启，请重试。'}
 // #endif
 // #ifdef MP-WEIXIN
 active.value=true
 // #endif
}
function cameraError(){stop();error.value='相机暂时无法开启，请检查小程序相机权限，或使用本地图片。'}
function choosePhoto(){stop();uni.chooseImage({count:1,sourceType:['album'],success:r=>{if(alive){photo.value=r.tempFilePaths[0];error.value=''}},fail:()=>{}})}
function touchStart(e){if(locked.value)return;const t=e.touches[0];if(t)touch={x:t.clientX,y:t.clientY,startX:x.value,startY:y.value}}
function touchMove(e){if(locked.value||!touch)return;const t=e.touches[0];if(t){x.value=Math.max(-size/2,Math.min(size/2,touch.startX+t.clientX-touch.x));y.value=Math.max(-size/2,Math.min(size/2,touch.startY+t.clientY-touch.y))}}
function reset(){x.value=0;y.value=0;angle.value=0;scale.value=90;opacity.value=65;locked.value=false}
function hidden(){if(document.hidden)stop()}
onReady(()=>{const ctx=uni.createCanvasContext('trace-source',instance.proxy);drawShapes(ctx,makeShapes(plan),size,[],true,()=>{if(!alive)return;uni.canvasToTempFilePath({canvasId:'trace-source',fileType:'png',success:r=>{if(alive)overlay.value=r.tempFilePath},fail:()=>{error.value='线稿未能加载，请返回重试'}},instance.proxy)})
 // #ifdef H5
 document.addEventListener('visibilitychange',hidden)
 // #endif
})
onHide(stop);onUnload(()=>{alive=false;stop()
 // #ifdef H5
 document.removeEventListener('visibilitychange',hidden)
 // #endif
})
</script>
<style scoped>.tracing-frame{position:relative;background:#eef2ed;border:1rpx solid #d5dfd8;border-radius:24rpx;overflow:hidden;touch-action:none;max-height:650px}.camera-surface{position:absolute;inset:0;width:100%;height:100%}.trace-overlay{position:absolute;left:50%;top:50%;width:100%;height:100%;pointer-events:none}.frame-hint{position:absolute;left:0;right:0;top:18rpx;text-align:center;font-size:var(--zy-text-caption, 24rpx);color:#789184}.frame-badge{position:absolute;bottom:16rpx;left:16rpx;padding:8rpx 14rpx;background:#ffffffd9;border-radius:10rpx;font-size:var(--zy-text-micro, 22rpx);color:#274639}.source-canvas{position:fixed;left:-9999px;top:0;pointer-events:none}.controls{margin-top:24rpx}.growth-row>.growth-button{flex:1;font-size:var(--zy-text-caption, 24rpx)}.lock{font-size:var(--zy-text-caption, 24rpx);background:#edf5f3;color:var(--zy-brand-700, #1a645e);margin:0;border-radius:12rpx}.lock::after{border:0}</style>
