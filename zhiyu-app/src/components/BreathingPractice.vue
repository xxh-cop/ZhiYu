<script setup>
import AccessibleButton from './AccessibleButton.vue'
import {ref,computed,onBeforeUnmount} from 'vue';import {onHide} from '@dcloudio/uni-app';
const running=ref(false),seconds=ref(0),phase=computed(()=>seconds.value%10<4?'轻轻吸气':'缓缓呼气'),remaining=computed(()=>seconds.value%10<4?4-seconds.value%10:10-seconds.value%10);let timer=null,start=0;
function stop(){running.value=false;clearInterval(timer);timer=null}
function toggle(){if(running.value){stop();return}seconds.value=0;start=Date.now();running.value=true;timer=setInterval(()=>{seconds.value=Math.floor((Date.now()-start)/1000);if(seconds.value>=60){stop();seconds.value=60}},200)}
onHide(stop);onBeforeUnmount(stop)
</script>
<template><view class="growth-card breath-card"><view><text class="growth-card-title">给自己一分钟</text><text class="growth-muted">跟随舒适的节奏，4 秒吸气、6 秒呼气。不必勉强，随时可以停止。</text><AccessibleButton class="growth-button secondary" :aria-pressed="running" @click="toggle">{{running?'停止练习':seconds===60?'再做一次':'开始呼吸练习'}}</AccessibleButton></view><view class="breath-circle" :class="{inhale:running&&seconds%10<4}"><text>{{running?phase:seconds===60?'已完成':'自然呼吸'}}</text><text v-if="running" class="breath-number">{{remaining}}</text></view></view></template>
<style scoped>.breath-card{display:flex;align-items:center;justify-content:space-between;gap:40rpx}.breath-card>view:first-child{flex:1}.breath-circle{flex-shrink:0;width:180rpx;height:180rpx;border-radius:50%;background:#e8efe4;border:12rpx solid var(--zy-border, #dfe7dc);display:flex;align-items:center;justify-content:center;flex-direction:column;color:var(--zy-brand-700, #1a645e);transition:transform 6s linear;transform:scale(.85);font-size:25rpx}.inhale{transform:scale(1);transition-duration:4s}.breath-number{font-size:38rpx;margin-top:8rpx}@media(prefers-reduced-motion:reduce){.breath-circle{transition:none;transform:none}}</style>
