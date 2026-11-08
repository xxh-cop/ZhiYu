<template>
	<!-- StitchDivider —— 缝线分隔（暖纸青玉 v6 · 规格 §2-C4）。纯装饰。 -->
	<view class="zy-stitch" :class="{ 'zy-stitch--v': vertical }" aria-hidden="true"></view>
</template>

<script setup>
defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
defineProps({ vertical: Boolean })
</script>

<style scoped>
.zy-stitch { border-top: 2rpx dashed var(--zy-stitch, #d8d8d4); }
.zy-stitch--v { border-top: 0; border-left: 2rpx dashed var(--zy-stitch, #d8d8d4); align-self: stretch; }
</style>
