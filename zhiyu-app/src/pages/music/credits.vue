<template>
	<view :class="zyThemeClass" class="growth-page"><ZyPageHead seal="源" title="每份创作，都有来处" subtitle="图片用于品类氛围展示，不代表教程成品。图片已裁切、缩小及压缩；音乐未改编。" /><view v-for="item in all" :key="item.source" class="growth-card"><text class="growth-card-title">{{item.original||item.file}}</text><text class="growth-muted">{{item.author}} · {{item.license}}</text><AccessibleButton class="growth-button secondary" @click="copy(item.source)">复制原始来源链接</AccessibleButton><text class="source">{{item.source}}</text></view><text class="growth-muted">CC0：公共领域贡献。CC BY 4.0：署名授权。许可文本：</text><AccessibleButton class="growth-button secondary" @click="copy('https://creativecommons.org/licenses/by/4.0/')">CC BY 4.0</AccessibleButton><AccessibleButton class="growth-button secondary" @click="copy('https://creativecommons.org/publicdomain/zero/1.0/')">CC0 1.0</AccessibleButton></view></template>
<script setup>
import ZyPageHead from '../../components/ZyPageHead.vue';import{tracks}from'../../data/music';import{imageCredits}from'../../data/media-credits';import AccessibleButton from'../../components/AccessibleButton.vue';const all=[...tracks,...imageCredits];function copy(data){uni.setClipboardData({data})}</script>
<style scoped>
.source {display:block;word-break:break-all;font-size:var(--zy-text-micro, 22rpx);color:var(--zy-muted);margin-top:16rpx;line-height:1.7}

.growth-card .growth-button.secondary {display:flex;justify-content:flex-start;background:transparent;border:0;border-bottom:1rpx solid var(--zy-border,#d3ded8);border-radius: var(--zy-radius-control, 18rpx);text-align:left;color:var(--zy-brand-700,#183369);font-size:24rpx;padding:8rpx 0;margin-top:4rpx;min-height:64rpx;line-height:1.9}
.growth-card .growth-button.secondary::after {border:0}
</style>
