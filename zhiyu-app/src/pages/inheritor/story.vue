<template>
	<view class="story-page zy-page-enter">
		<view class="story-nav">
			<AccessibleButton class="back-btn" aria-label="返回传承人列表" @click="leave">‹ 返回</AccessibleButton>
			<text class="nav-label">手艺人的故事</text>
		</view>

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
	background: var(--zy-bg, #f5f7f2);
	color: var(--zy-content, #4a5f54);
}
.story-nav { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.back-btn { margin: 0; padding: 8rpx 18rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 999rpx; background: var(--zy-card-bg, #fff); color: var(--zy-content, #4a5f54); font-size: 22rpx; line-height: 2; }
.back-btn::after { border: 0; }
.nav-label { font-size: 20rpx; letter-spacing: 2rpx; color: var(--zy-sub, #4a5f54); }
.story-hero { position: relative; margin: 32rpx 0 24rpx; padding: 34rpx 28rpx 30rpx; overflow: hidden; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 26rpx; background: linear-gradient(140deg, var(--zy-card-bg, #fff), var(--zy-bg-tint-mint, #eaf5ef)); }
.story-hero::after { content: ''; position: absolute; right: -60rpx; bottom: -80rpx; width: 240rpx; height: 240rpx; border-radius: 50%; border: 1rpx solid rgba(47, 117, 102, 0.16); }
.eyebrow { display: block; font-size: 20rpx; letter-spacing: 2rpx; color: var(--zy-accent, #8a6d1f); }
.title { display: block; position: relative; z-index: 1; margin-top: 12rpx; font-family: var(--zy-serif); font-size: 44rpx; font-weight: 600; line-height: 1.4; color: var(--zy-title, #233d34); }
.subtitle { display: block; position: relative; z-index: 1; margin-top: 10rpx; font-size: 24rpx; line-height: 1.7; color: var(--zy-sub, #4a5f54); }
.synthetic-badge { display: inline-block; position: relative; z-index: 1; margin-top: 18rpx; padding: 7rpx 14rpx; border: 1rpx solid rgba(138, 109, 31, 0.42); border-radius: 999rpx; background: rgba(138, 109, 31, 0.1); color: var(--zy-accent, #8a6d1f); font-size: 19rpx; }
.story-body { padding: 26rpx 24rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 24rpx; background: var(--zy-card-bg, #fff); box-shadow: var(--zy-shadow-sm); }
.catalog-note { display: block; padding: 16rpx 18rpx; border-radius: 12rpx; background: var(--zy-bg, #f3f6f4); font-size: 20rpx; line-height: 1.8; color: var(--zy-sub, #4a5f54); }
.section-block { margin-top: 30rpx; }
.section-heading { display: block; font-family: var(--zy-serif); font-size: 31rpx; font-weight: 600; color: var(--zy-title, #233d34); }
.section-body { display: block; margin-top: 12rpx; font-size: 25rpx; line-height: 2; color: var(--zy-content, #4a5f54); }
.quote-card { position: relative; margin-top: 30rpx; padding: 24rpx 24rpx 20rpx; border-left: 5rpx solid var(--zy-accent, #8a6d1f); border-radius: 0 16rpx 16rpx 0; background: var(--zy-bg-tint-mint, #e8efe4); }
.quote-mark { display: block; font-family: var(--zy-serif); font-size: 48rpx; line-height: 0.8; color: var(--zy-accent, #8a6d1f); }
.quote-text { display: block; margin-top: 10rpx; font-family: var(--zy-serif); font-size: 27rpx; line-height: 1.85; color: var(--zy-title, #233d34); }
.quote-note { display: block; margin-top: 10rpx; font-size: 19rpx; color: var(--zy-sub, #4a5f54); }
.prompt-card { margin-top: 24rpx; padding: 22rpx 24rpx; border: 1rpx dashed var(--zy-border, #dfe7dc); border-radius: 16rpx; }
.prompt-label { display: block; font-size: 20rpx; letter-spacing: 1rpx; color: var(--zy-brand-700, #217e76); }
.prompt-text { display: block; margin-top: 8rpx; font-size: 24rpx; line-height: 1.8; color: var(--zy-title, #233d34); }
.sources-card { margin-top: 30rpx; padding-top: 24rpx; border-top: 1rpx solid var(--zy-border, #dfe7dc); }
.sources-title { display: block; font-size: 22rpx; font-weight: 600; color: var(--zy-title, #233d34); }
.source-row { margin-top: 14rpx; padding: 16rpx 18rpx; border-radius: 12rpx; background: var(--zy-bg, #f3f6f4); }
.source-row:focus-visible { outline: 3px solid var(--zy-primary, #217e76); outline-offset: 2px; }
.source-title { display: block; font-size: 22rpx; line-height: 1.6; color: var(--zy-brand-700, #217e76); }
.source-org { display: block; margin-top: 4rpx; font-size: 19rpx; line-height: 1.6; color: var(--zy-sub, #4a5f54); }
.source-url { display: block; margin-top: 4rpx; overflow: hidden; font-size: 18rpx; line-height: 1.5; color: var(--zy-sub, #4a5f54); text-overflow: ellipsis; white-space: nowrap; }
.boundary-note { display: block; margin-top: 26rpx; font-size: 20rpx; line-height: 1.8; color: var(--zy-sub, #4a5f54); }
.disclaimer { display: block; margin-top: 26rpx; text-align: center; font-size: 20rpx; color: var(--zy-sub, #4a5f54); }
.primary-action { min-height: 88rpx; margin-top: 18rpx; border-radius: 999rpx; background: var(--zy-brand-700, #217e76); color: #fff; font-size: 27rpx; line-height: 2.9; }
.primary-action::after { border: 0; }
.text-action { margin-top: 12rpx; background: transparent; color: var(--zy-sub, #4a5f54); font-size: 23rpx; line-height: 2.4; }
.text-action::after { border: 0; }
.status-card { margin-top: 40rpx; padding: 50rpx 28rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 24rpx; background: var(--zy-card-bg, #fff); text-align: center; }
.status-text { font-size: 25rpx; line-height: 1.8; color: var(--zy-content, #4a5f54); }
.story-loading { margin-top: 40rpx; }
.hero-skeleton { height: 260rpx; border-radius: 24rpx; }
.line-skeleton { height: 34rpx; margin-top: 22rpx; border-radius: 10rpx; }
.line-skeleton.short { width: 64%; }
@media (max-width: 360px) {
	.story-page { padding-left: 24rpx; padding-right: 24rpx; }
	.title { font-size: 38rpx; }
}
</style>
