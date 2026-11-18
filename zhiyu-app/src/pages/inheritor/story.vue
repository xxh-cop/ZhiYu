<template>
	<view :class="zyThemeClass" class="story-page zy-page-enter">

		<view v-if="loading" class="story-loading">
			<view class="hero-skeleton zy-skeleton" aria-hidden="true"></view>
			<view class="line-skeleton zy-skeleton" aria-hidden="true"></view>
			<view class="line-skeleton short zy-skeleton" aria-hidden="true"></view>
		</view>

		<view v-else-if="errorMsg" class="status-card" role="alert">
			<text class="status-text">{{ errorMsg }}</text>
			<AccessibleButton class="primary-action" @click="load">重试</AccessibleButton>
			<AccessibleButton class="text-action" @click="leave">返回列表</AccessibleButton>
		</view>

		<template v-else-if="story">
			<view class="story-hero">
				<text class="eyebrow">{{ story.traditionName }} · {{ story.readingMinutes }} 分钟阅读</text>
				<text class="title">{{ story.title }}</text>
				<text class="subtitle">{{ story.subtitle }}</text>
				<view v-if="story.synthetic" class="synthetic-badge">合成教学叙事 · 非真实人物访谈</view>
			</view>

			<view class="story-body">
				<text class="catalog-note">{{ story.disclaimer }}</text>

				<view v-for="section in story.sections" :key="section.heading" class="section-block">
					<text class="section-heading">{{ section.heading }}</text>
					<text class="section-body">{{ section.body }}</text>
				</view>

				<view class="quote-card">
					<text class="quote-mark">“</text>
					<text class="quote-text">{{ story.quote }}</text>
					<text class="quote-note">教学叙事中的一句话</text>
				</view>

				<view class="prompt-card">
					<text class="prompt-label">留给你的观察</text>
					<text class="prompt-text">{{ story.learningPrompt }}</text>
				</view>

				<view class="sources-card">
					<text class="sources-title">工艺背景来源</text>
					<view
						v-for="source in story.sources"
						:key="source.id"
						class="source-row"
						role="link"
						tabindex="0"
						@click="openSource(source.url)"
						@keydown.enter.prevent="openSource(source.url)"
						@keydown.space.prevent="openSource(source.url)"
					>
						<text class="source-title">{{ source.title }}</text>
						<text class="source-org">{{ source.organization }}</text>
						<text class="source-url">{{ source.url }}</text>
					</view>
				</view>

				<text class="boundary-note">{{ story.boundary }}</text>
				<text class="disclaimer">本系统不能替代专业心理咨询</text>
			</view>
		</template>
		<Toast />
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import Toast from '../../components/Toast.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()

const storyId = ref('')
const story = ref(null)
const loading = ref(true)
const errorMsg = ref('')

onLoad((query) => {
	storyId.value = query?.id || ''
	load()
})

async function load() {
	if (!storyId.value) {
		loading.value = false
		errorMsg.value = '没有找到这个故事，请重新选择。'
		return
	}
	loading.value = true
	errorMsg.value = ''
	try {
		story.value = await http.get(`/api/inheritors/stories/${encodeURIComponent(storyId.value)}`)
	} catch (e) {
		story.value = null
		errorMsg.value = e?.code === 1004 ? '这个故事不存在，可能已经调整。' : (e?.message || '故事读取失败，请重试')
	} finally {
		loading.value = false
	}
}

function openSource(url) {
	if (!url) return
	// #ifdef H5
	window.open(url, '_blank', 'noopener,noreferrer')
	// #endif
	// #ifndef H5
	uni.setClipboardData({
		data: url,
		success: () => toast.show('来源链接已复制'),
	})
	// #endif
}

function leave() {
	uni.navigateBack({
		fail: () => uni.reLaunch({ url: '/pages/inheritor/list' }),
	})
}
</script>

<style scoped>
.story-page {
	min-height: 100vh;
	padding: 28rpx 32rpx 90rpx;
	box-sizing: border-box;
	background: var(--zy-bg, #f1f4f1);
	color: var(--zy-text, #42524c);
}
.story-hero { position: relative; margin: 0 0 24rpx; padding: 30rpx 0 28rpx; overflow: visible; border: 0; border-bottom: 1rpx solid var(--zy-border, #d3ded8); border-radius: 0; background: transparent; }
.story-hero::after { display: none; }
.eyebrow { display: block; font-size:var(--zy-text-micro, 22rpx); letter-spacing: 2px; color: var(--zy-muted, #6e7b75); }
.title { display: block; position: relative; z-index: 1; margin-top: 12rpx; font-family: var(--zy-font-display, serif); font-size:56rpx; font-weight: 500; line-height: 1.35; color: var(--zy-title, #111816); }
.subtitle { display: block; position: relative; z-index: 1; margin-top: 10rpx; font-size:var(--zy-text-caption, 24rpx); line-height: 1.8; color: var(--zy-muted, #6e7b75); }
.synthetic-badge { display: inline-block; position: relative; z-index: 1; margin-top: 16rpx; padding: 5rpx 12rpx; border: 0; border-left: 2rpx solid var(--zy-care, #a64842); border-radius: 0; background: transparent; color: var(--zy-care, #a64842); font-size:var(--zy-text-micro, 22rpx); }
.story-body { padding: 24rpx 0; border: 0; border-top: 1rpx solid var(--zy-border, #d3ded8); border-bottom: 1rpx solid var(--zy-border, #d3ded8); border-radius: 0; background: transparent; box-shadow: none; }
.catalog-note { display: block; padding: 12rpx 0 12rpx 16rpx; border-left: 2rpx solid var(--zy-border, #d3ded8); border-radius: 0; background: transparent; font-size:var(--zy-text-micro, 22rpx); line-height: 1.8; color: var(--zy-muted, #6e7b75); }
.section-block { margin-top: 30rpx; }
.section-heading { display: block; font-family: var(--zy-font-display, serif); font-size:36rpx; font-weight: 500; color: var(--zy-title, #111816); }
.section-body { display: block; margin-top: 12rpx; font-size:var(--zy-text-caption, 24rpx); line-height: 2; color: var(--zy-text, #42524c); }
.quote-card { position: relative; margin-top: 28rpx; padding: 16rpx 0 16rpx 20rpx; border-left: 3rpx solid var(--zy-care, #a64842); border-radius: 0; background: transparent; }
.quote-mark { display: block; font-family: var(--zy-font-display, serif); font-size:48rpx; line-height: 0.8; color: var(--zy-care, #a64842); }
.quote-text { display: block; margin-top: 10rpx; font-family: var(--zy-font-display, serif); font-size:var(--zy-text-body, 28rpx); line-height: 1.9; color: var(--zy-title, #111816); }
.quote-note { display: block; margin-top: 10rpx; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-muted, #6e7b75); }
.prompt-card { margin-top: 24rpx; padding: 20rpx 0; border: 0; border-top: 1rpx solid var(--zy-border, #d3ded8); border-radius: 0; }
.prompt-label { display: block; font-size:var(--zy-text-micro, 22rpx); letter-spacing: 1rpx; color: var(--zy-muted, #6e7b75); }
.prompt-text { display: block; margin-top: 8rpx; font-size:var(--zy-text-caption, 24rpx); line-height: 1.8; color: var(--zy-text, #42524c); }
.sources-card { margin-top: 28rpx; padding-top: 20rpx; border-top: 1rpx solid var(--zy-border, #d3ded8); }
.sources-title { display: block; font-size:var(--zy-text-micro, 22rpx); font-weight: 500; color: var(--zy-title, #111816); }
.source-row { margin-top: 12rpx; padding: 12rpx 0; border-bottom: 1rpx solid var(--zy-border, #d3ded8); border-radius: 0; background: transparent; }
.source-row:focus-visible { outline: 3px solid var(--zy-brand-600, #0e5b53); outline-offset: 2px; }
.source-title { display: block; font-size:var(--zy-text-micro, 22rpx); line-height: 1.6; color: var(--zy-brand-700, #0a403b); }
.source-org { display: block; margin-top: 4rpx; font-size:var(--zy-text-micro, 22rpx); line-height: 1.6; color: var(--zy-muted, #6e7b75); }
.source-url { display: block; margin-top: 4rpx; overflow: hidden; font-size:var(--zy-text-micro, 22rpx); line-height: 1.5; color: var(--zy-muted, #6e7b75); text-overflow: ellipsis; white-space: nowrap; }
.boundary-note { display: block; margin-top: 26rpx; font-size:var(--zy-text-micro, 22rpx); line-height: 1.8; color: var(--zy-muted, #6e7b75); }
.disclaimer { display: block; margin-top: 26rpx; text-align: center; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-muted, #6e7b75); }
.primary-action { min-height: 88rpx; margin-top: 18rpx; border-radius: 0; background: var(--zy-brand-600, #0e5b53); color: #fff; font-size:var(--zy-text-body, 28rpx); line-height: 2.9; }
.primary-action::after { border: 0; }
.text-action { margin-top: 12rpx; background: transparent; color: var(--zy-brand-700, #0a403b); font-size:var(--zy-text-caption, 24rpx); line-height: 2.4; }
.text-action::after { border: 0; }
.status-card { margin-top: 32rpx; padding: 40rpx 0; border: 0; border-top: 1rpx solid var(--zy-border, #d3ded8); border-bottom: 1rpx solid var(--zy-border, #d3ded8); border-radius: 0; background: transparent; text-align: center; }
.status-text { font-size:var(--zy-text-caption, 24rpx); line-height: 1.8; color: var(--zy-text, #42524c); }
.story-loading { margin-top: 40rpx; }
.hero-skeleton { height: 260rpx; border-radius: 0; }
.line-skeleton { height: 34rpx; margin-top: 22rpx; border-radius: 0; }
.line-skeleton.short { width: 64%; }
@media (max-width: 360px) {
	.story-page { padding-left: 24rpx; padding-right: 24rpx; }
	.title { font-size:var(--zy-text-title-lg, 40rpx); }
}
</style>
