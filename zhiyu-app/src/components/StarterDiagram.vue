<template><view class="diagram" :class="kind" aria-hidden="true"><view v-for="n in 9" :key="n" class="piece" :style="{'--i':n}"></view></view></template>
<script setup>defineProps({kind:{type:String,default:'wave'}})</script>
<style scoped>
.diagram{height:220rpx;position:relative;overflow:hidden;border-radius:18rpx;background:#edf5f2;margin:20rpx 0}.piece{position:absolute;box-sizing:border-box;left:calc(18% + var(--i)*5%);top:32rpx;width:20rpx;height:155rpx;background:#3d8d80;border-radius:4rpx;opacity:.85}.piece:nth-child(2n){background:#dbb370}
.fold .piece,.fan .piece{left:44%;top:36rpx;width:36rpx;transform-origin:50% 90%;transform:rotate(calc((var(--i) - 5)*15deg));border:1rpx solid #fff}
.leaf .piece{width:65rpx;height:130rpx;left:calc(12% + var(--i)*6%);transform:rotate(-35deg);border-radius:90% 0 90% 0;top:45rpx}.leaf .piece:nth-child(n+6){display:none}
.weave .piece:nth-child(n+5){height:20rpx;width:60%;left:20%;top:calc(32rpx + (var(--i) - 5)*32rpx);background:#dbb370}.weave .piece:nth-child(-n+4){left:calc(20% + var(--i)*11%)}
.braid .piece{width:28rpx;height:95rpx;left:38%;top:calc(8rpx + var(--i)*16rpx);transform:rotate(42deg)}.braid .piece:nth-child(2n){left:50%;transform:rotate(-42deg)}
.wrap .piece{left:20%;width:60%;height:12rpx;top:calc(20rpx + var(--i)*19rpx);transform:rotate(-14deg)}
.wave .piece{background:transparent;border:3rpx solid #3d8d80;border-radius:50%;width:calc(20rpx + var(--i)*35rpx);height:calc(10rpx + var(--i)*20rpx);left:50%;top:50%;transform:translate(-50%,-50%)}
.hills .piece{width:70%;height:150rpx;left:calc(var(--i)*5% - 20%);top:calc(35rpx + var(--i)*19rpx);border-radius:70% 60% 0 0}.hills .piece:nth-child(n+4){display:none}
.rings .piece{background:transparent;width:95rpx;height:95rpx;top:60rpx;left:calc(4% + var(--i)*10%);border:10rpx solid #3d8d80;border-radius:50%}.rings .piece:nth-child(2n){border-color:#dbb370}.rings .piece:nth-child(n+6){display:none}
.dots .piece,.mirror .piece{width:24rpx;height:24rpx;border-radius:50%;top:calc(30rpx + var(--i)*14rpx)}.mirror .piece:nth-child(2n){left:calc(80% - var(--i)*5%)}
.stitch .piece{height:8rpx;width:30rpx;top:calc(20rpx + var(--i)*17rpx);transform:rotate(-20deg)}.grid .piece{width:45rpx;height:45rpx;left:calc(12% + var(--i)*7%);top:calc(20rpx + var(--i)*13rpx);border:3rpx solid #fff}
</style>
