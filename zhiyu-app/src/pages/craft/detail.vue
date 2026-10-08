<template>
	<view class="page zy-page-enter">
		<!-- 加载中 -->
		<view v-if="loading" class="status-box">
			<wd-loading color="#2D9B8F" />
			<text class="status-text">正在加载…</text>
		</view>

		<!-- 错误状态 -->
		<view v-else-if="errorMsg" class="status-box">
			<text class="error-text">{{ errorMsg }}</text>
			<wd-button type="primary" plain size="small" @click="loadData">重试</wd-button>
		</view>

		<!-- 项目详情 -->
		<view v-else-if="project" class="detail-content">
			<!-- ═══ 顶部封面区 ═══ -->
			<view class="cover-section zy-slide-up zy-stagger" :style="coverStyle(project.coverUrl)" style="--stagger-i: 0">
				<view class="cover-overlay"></view>
				<view class="cover-info">
					<text class="cover-title">{{ project.title }}</text>
					<view class="cover-tags">
						<view v-if="project.category" class="cover-tag">
							<text class="cover-tag-text">{{ project.category }}</text>
						</view>
						<view class="cover-tag">
							<text class="cover-tag-text">{{ levelLabel(project.level) }}</text>
						</view>
						<view class="cover-tag">
							<text class="cover-tag-text">约 {{ project.estMinutes }} 分钟</text>
						</view>
					</view>
					<view v-if="therapyTags.length" class="cover-tags">
						<view
							v-for="tag in therapyTags"
							:key="tag"
							class="therapy-tag"
						>
							<text class="therapy-tag-text">{{ tag }}</text>
						</view>
					</view>
				</view>
			</view>

			<!-- ═══ 传承人信息条 ═══ -->
			<view
				v-if="project.inheritorId"
				class="inheritor-bar zy-slide-up zy-stagger"
				style="--stagger-i: 1"
				@click="goInheritorDetail"
			>
				<view class="inheritor-avatar" :style="inheritorAvatarStyle">
					<text v-if="!hasInheritorAvatar" class="inheritor-avatar-letter">{{ firstLetter(project.inheritorNickname) }}</text>
				</view>
				<view class="inheritor-info">
					<text class="inheritor-name">{{ project.inheritorNickname || '传承人' }}</text>
					<text v-if="project.inheritorSkill" class="inheritor-skill">{{ project.inheritorSkill }}</text>
				</view>
				<wd-icon name="arrow-right" size="32rpx" color="#9CA3AF" />
			</view>

			<!-- ═══ 文化背景 ═══ -->
			<view v-if="project.cultureBg" class="section-card zy-slide-up zy-stagger" style="--stagger-i: 2">
				<text class="section-title">文化背景</text>
				<text class="section-text">{{ project.cultureBg }}</text>
			</view>

			<!-- ═══ 所需材料 ═══ -->
			<view v-if="materials.length" class="section-card zy-slide-up zy-stagger" style="--stagger-i: 3">
				<text class="section-title">所需材料</text>
				<view class="material-list">
					<view
						v-for="(mat, idx) in materials"
						:key="mat.id || idx"
						class="material-item"
					>
						<view class="material-dot"></view>
						<text class="material-name">{{ mat.name }}</text>
						<text v-if="mat.qty" class="material-qty">{{ mat.qty }}</text>
					</view>
				</view>
			</view>

			<!-- ═══ 创作步骤 ═══ -->
			<view v-if="steps.length" class="section-card zy-slide-up zy-stagger" style="--stagger-i: 4">
				<text class="section-title">创作步骤</text>
				<view class="step-list">
					<view
						v-for="(step, idx) in steps"
						:key="step.id || idx"
						class="step-item"
					>
						<view class="step-num">
							<text class="step-num-text">{{ step.stepNo || idx + 1 }}</text>
						</view>
						<view class="step-content">
							<text class="step-title">{{ step.title }}</text>
						</view>
					</view>
				</view>
			</view>

			<!-- 底部占位（避免被固定按钮遮挡） -->
			<view class="bottom-spacer"></view>
		</view>

		<!-- 底部固定按钮 -->
		<view v-if="project" class="bottom-bar">
			<view class="start-btn" @click="goSession">
				<text class="start-btn-text">开始创作</text>
			</view>
		</view>

		<!-- 底部免责声明 -->
		<text v-if="project" class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '../../utils/request'

const projectId = ref(null)
const project = ref(null)
const loading = ref(false)
const errorMsg = ref('')

const therapyTags = computed(() => {
	if (!project.value || !project.value.therapyTags) return []
	return project.value.therapyTags.split(',').map((t) => t.trim()).filter(Boolean)
})

const materials = computed(() => {
	if (!project.value || !project.value.materials) return []
	return project.value.materials
})

const steps = computed(() => {
	if (!project.value || !project.value.steps) return []
	return project.value.steps
})

const hasInheritorAvatar = computed(() => {
	// #ifdef H5
	return !!(project.value && project.value.inheritorId && false) // 后端暂不返回 inheritorAvatarUrl
	// #endif
	return false
})

const inheritorAvatarStyle = computed(() => {
	return 'background: linear-gradient(135deg, #2D9B8F, #5BCFBF);'
})

onLoad((query) => {
	projectId.value = query.id
	loadData()
})

async function loadData() {
	loading.value = true
	errorMsg.value = ''
	try {
		const data = await http.get(`/api/craft/projects/${projectId.value}`)
		project.value = data
	} catch (e) {
		errorMsg.value = (e && e.message) || '加载失败，请稍后重试'
	} finally {
		loading.value = false
	}
}

function firstLetter(name) {
	if (!name) return '?'
	return name.charAt(0)
}

function coverStyle(coverUrl) {
	// #ifdef H5
	if (coverUrl) {
		return `background-image: url(${coverUrl}); background-size: cover; background-position: center;`
	}
	// #endif
	return 'background: linear-gradient(135deg, #2D9B8F 0%, #5BC4A8 100%);'
}

function levelLabel(level) {
	if (level === 1) return '入门'
	if (level === 2) return '进阶'
	if (level === 3) return '高阶'
	return ''
}

function goInheritorDetail() {
	if (project.value && project.value.inheritorProfileId) {
		uni.navigateTo({
			url: `/pages/inheritor/detail?id=${project.value.inheritorProfileId}`,
		})
	} else {
		uni.showToast({ title: '传承人主页暂不可用', icon: 'none' })
	}
}

function goSession() {
	if (project.value) {
		uni.navigateTo({
			url: `/pages/craft/session?projectId=${projectId.value}&projectTitle=${encodeURIComponent(project.value.title)}`,
		})
	}
}
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	padding: 0 0 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #F8F9FB);
}

/* ── 状态区 ── */
.status-box {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 120rpx 0;
	gap: 24rpx;
}

.status-text {
	font-size: 28rpx;
	color: var(--zy-aid, #9CA3AF);
}

.error-text {
	font-size: 28rpx;
	color: var(--zy-danger, #FF6B6B);
}

/* ── 封面区 ── */
.cover-section {
	position: relative;
	height: 400rpx;
	display: flex;
	align-items: flex-end;
	padding: 32rpx 24rpx;
	box-sizing: border-box;
}

.cover-overlay {
	position: absolute;
	left: 0;
	right: 0;
	bottom: 0;
	height: 60%;
	background: linear-gradient(to top, rgba(31, 41, 55, 0.7) 0%, transparent 100%);
}

.cover-info {
	position: relative;
	z-index: 1;
}

.cover-title {
	font-size: 44rpx;
	font-weight: 600;
	color: #FFFFFF;
	line-height: 1.3;
	text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.3);
}

.cover-tags {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	margin-top: 16rpx;
}

.cover-tag {
	padding: 6rpx 20rpx;
	border-radius: 999rpx;
	background-color: rgba(255, 255, 255, 0.2);
	backdrop-filter: blur(8px);
}

.cover-tag-text {
	font-size: 22rpx;
	color: #FFFFFF;
}

.therapy-tag {
	padding: 6rpx 20rpx;
	border-radius: 999rpx;
	background-color: rgba(45, 155, 143, 0.3);
}

.therapy-tag-text {
	font-size: 22rpx;
	color: #FFFFFF;
}

/* ── 传承人信息条 ── */
.inheritor-bar {
	display: flex;
	align-items: center;
	gap: 16rpx;
	margin: 24rpx;
	padding: 24rpx;
	border-radius: 16px;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1px solid var(--zy-border, #E5E7EB);
	box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
	transition: transform 0.2s ease;
}

.inheritor-bar:active {
	transform: scale(0.98);
}

.inheritor-avatar {
	width: 72rpx;
	height: 72rpx;
	border-radius: 50%;
	flex-shrink: 0;
	display: flex;
	align-items: center;
	justify-content: center;
}

.inheritor-avatar-letter {
	font-size: 32rpx;
	font-weight: 600;
	color: #FFFFFF;
}

.inheritor-info {
	flex: 1;
	display: flex;
	flex-direction: column;
}

.inheritor-name {
	font-size: 30rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

.inheritor-skill {
	margin-top: 4rpx;
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

/* ── 区块卡片 ── */
.section-card {
	margin: 0 24rpx 24rpx;
	padding: 28rpx;
	border-radius: 16px;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1px solid var(--zy-border, #E5E7EB);
	box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.section-title {
	font-size: 32rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
	font-family: var(--zy-serif);
	display: block;
	margin-bottom: 16rpx;
}

.section-text {
	font-size: 28rpx;
	line-height: 1.8;
	color: var(--zy-content, #4B5563);
}

/* ── 材料列表 ── */
.material-list {
	display: flex;
	flex-direction: column;
	gap: 20rpx;
}

.material-item {
	display: flex;
	align-items: center;
	gap: 16rpx;
}

.material-dot {
	width: 12rpx;
	height: 12rpx;
	border-radius: 50%;
	background-color: var(--wot-color-theme, #2D9B8F);
	flex-shrink: 0;
}

.material-name {
	font-size: 28rpx;
	color: var(--zy-title, #1F2937);
}

.material-qty {
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
	margin-left: auto;
}

/* ── 步骤列表 ── */
.step-list {
	display: flex;
	flex-direction: column;
	gap: 24rpx;
}

.step-item {
	display: flex;
	align-items: center;
	gap: 20rpx;
}

.step-num {
	width: 56rpx;
	height: 56rpx;
	border-radius: 50%;
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.step-num-text {
	font-size: 28rpx;
	font-weight: 600;
	color: var(--wot-color-theme, #2D9B8F);
}

.step-content {
	flex: 1;
}

.step-title {
	font-size: 28rpx;
	color: var(--zy-title, #1F2937);
	line-height: 1.5;
}

/* ── 底部占位 ── */
.bottom-spacer {
	height: 40rpx;
}

/* ── 底部固定按钮 ── */
.bottom-bar {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	padding: 20rpx 24rpx;
	padding-bottom: calc(20rpx + env(safe-area-inset-bottom, 0));
	background-color: var(--zy-bg, #F8F9FB);
	border-top: 1px solid var(--zy-border, #E5E7EB);
	z-index: 100;
}

.start-btn {
	height: 88rpx;
	border-radius: 999rpx;
	background: linear-gradient(135deg, #2D9B8F, #5BC4A8);
	display: flex;
	align-items: center;
	justify-content: center;
	transition: transform 0.2s ease;
}

.start-btn:active {
	transform: scale(0.98);
}

.start-btn-text {
	font-size: 32rpx;
	font-weight: 500;
	color: #FFFFFF;
}

/* ── 底部免责 ── */
.footnote {
	display: block;
	margin: 24rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
	text-align: center;
}
</style>
