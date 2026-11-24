<template>
	<view class="growth-page" :class="[{'motion-reduced':reducedMotion}, zyThemeClass]">
 <view class="growth-hero"><ZyPageHead seal="援" title="需要时，找到真实的支持" /><text class="growth-subtitle">面向{{campus.institution}}同学的公开联系指南。信息整理不代表校方授权或服务合作。</text></view>
 <view class="growth-card"><text class="growth-card-title">{{campus.center.name}}</text><text class="growth-muted">咨询联系电话 · 非紧急救援电话</text><text class="phone">{{campus.center.phone}}</text><text class="growth-muted">{{campus.center.address}}</text><text class="growth-muted">{{campus.center.hours}}</text><AccessibleButton class="growth-button" @click="call">联系中心确认预约</AccessibleButton><AccessibleButton class="growth-button secondary" @click="copy(campus.center.phone)">复制咨询电话</AccessibleButton><AccessibleButton class="growth-button secondary" @click="copy(campus.center.website)">复制中心官网链接</AccessibleButton></view>
 <view class="growth-card"><text class="growth-card-title">第一次联系，可以这样准备</text><text class="growth-muted">1. 先确认当前校区、服务对象、预约方式与可约时段。</text><text class="growth-muted">2. 可简单说“我想了解学生心理咨询预约”，不必先发出完整的个人经历。</text><text class="growth-muted">3. 询问咨询形式、保密说明和取消预约方式；是否继续由你决定。</text><text class="growth-muted">页面不会自动发送对话、自评或联系方式；点击复制也不会发出邮件或提交预约。</text></view>
 <view class="growth-card"><text class="growth-card-title">{{campus.college.name}}</text><text class="growth-muted">{{campus.college.email}}</text><text class="growth-muted">{{campus.college.purpose}}</text><AccessibleButton class="growth-button secondary" @click="copy(campus.college.email)">复制学院邮箱</AccessibleButton><AccessibleButton class="growth-button secondary" @click="copy(campus.college.website)">复制学院官网链接</AccessibleButton></view>
 <view class="growth-card"><text class="growth-card-title">如果此刻有紧迫的安全风险</text><text class="growth-muted">先到有他人在场的安全位置，联系身边可信任的人或当地紧急服务，不必等待预约或 AI 回复。</text><AccessibleButton class="growth-button secondary" @click="goCrisis">查看支持与求助入口</AccessibleButton></view>
 <text class="growth-muted">来源：中心及学院官网 · 核对日期 {{campus.verifiedAt}}。如信息变化，以校方最新公告为准。</text>
 <Toast/>
</view></template>
<script setup>
import ZyPageHead from '../../components/ZyPageHead.vue'
import{campusSupport as campus}from'../../data/campus-support';import{useInteraction}from'../../composables/useInteraction';import Toast from'../../components/Toast.vue';import AccessibleButton from'../../components/AccessibleButton.vue';import{useToast}from'../../composables/useToast';const{reducedMotion}=useInteraction();const toast=useToast()
function copy(data){uni.setClipboardData({data,success:()=>toast.success('已复制'),fail:()=>toast.care('复制失败，请手动选择文本')})}
function call(){uni.makePhoneCall({phoneNumber:campus.center.phone,fail:()=>toast.care('当前设备无法拨号，可复制号码后联系')})}
function goCrisis(){uni.navigateTo({url:'/pages/crisis/index'})}
</script>

<style scoped>
.growth-muted {display:block;margin-top:8rpx;overflow-wrap:anywhere}

.phone {display:block;font-family:var(--zy-font-display,serif);font-size:44rpx;font-weight:500;letter-spacing:1rpx;margin:20rpx 0;color:var(--zy-title,#111816)}
.growth-card .growth-button.secondary {background:transparent;border:0;border-bottom:1rpx solid var(--zy-border,#d3ded8);border-radius: var(--zy-radius-control, 18rpx);text-align:left;color:var(--zy-brand-700,#183369);padding:14rpx 0;margin-top:0;line-height:2.4}
</style>
