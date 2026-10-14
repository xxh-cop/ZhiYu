<template>
	<view class="page zy-page-enter">
		<!-- 加载中 -->
		<view v-if="loading" class="status-box">
			<wd-loading color="#2f7566" />
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
				</view>
			</view>

			<CraftCover v-if="!project.coverUrl" :category="project.category"/><MediaVideo v-if="project.videoUrl" :src="project.videoUrl" caption="传承人教学视频"/><!-- ═══ 信息条（pill 标签） ═══ -->
			<view class="info-pills zy-slide-up zy-stagger" style="--stagger-i: 1">
				<view v-if="project.category" class="pill">
					<wd-icon name="grid" size="24rpx" color="#2f7566" />
					<text class="pill-text">{{ project.category }}</text>
				</view>
				<view class="pill">
					<wd-icon name="chart-bar" size="24rpx" color="#bb914d" />
					<text class="pill-text">{{ levelLabel(project.level) }}</text>
				</view>
				<view class="pill">
					<wd-icon name="time" size="24rpx" color="#82ad9b" />
					<text class="pill-text">约 {{ project.estMinutes }} 分钟</text>
				</view>
			</view>

			<!-- ═══ 疗愈标签 ═══ -->
			<view v-if="therapyTags.length" class="therapy-tags zy-slide-up zy-stagger" style="--stagger-i: 2">
				<view
					v-for="tag in therapyTags"
					:key="tag"
					class="therapy-tag"
				>
					<text class="therapy-tag-text">{{ tag }}</text>
				</view>
			</view>

			<!-- ═══ 传承人信息条 ═══ -->
			<view
				v-if="project.inheritorProfileId"
				class="inheritor-bar zy-slide-up zy-stagger"
				style="--stagger-i: 3"
				@click="goInheritorDetail"
			>
				<view class="inheritor-avatar" :style="inheritorAvatarStyle">
					<text v-if="!hasInheritorAvatar" class="inheritor-avatar-letter">{{ firstLetter(project.inheritorNickname) }}</text>
				</view>
				<view class="inheritor-info">
					<text class="inheritor-name">{{ project.inheritorNickname || '传承人' }}</text>
					<text v-if="project.inheritorSkill" class="inheritor-skill">{{ project.inheritorSkill }}</text>
				</view>
				<wd-icon name="arrow-right" size="32rpx" color="#718478" />
			</view>

			<!-- ═══ 文化背景 ═══ -->
			<view v-if="project.cultureBg" class="section-card zy-slide-up zy-stagger" style="--stagger-i: 4">
				<view class="section-quote">"</view>
				<text class="section-title">文化背景</text>
				<text class="section-text">{{ project.cultureBg }}</text>
			</view>

			<CraftSafety :project-id="projectId" />
			<!-- ═══ 所需材料 ═══ -->
			<view v-if="materials.length" class="section-card zy-slide-up zy-stagger" style="--stagger-i: 5">
				<text class="section-title">所需材料</text>
				<view class="material-grid">
					<view
						v-for="(mat, idx) in materials"
						:key="mat.id || idx"
						class="material-item"
					>
						<view class="material-icon">
							<wd-icon name="gift" size="28rpx" color="#2f7566" />
						</view>
						<text class="material-name">{{ mat.name }}</text>
						<text v-if="mat.qty" class="material-qty">{{ mat.qty }}</text>
					</view>
				</view>
			</view>

			<!-- ═══ 创作步骤（时间线） ═══ -->
			<view v-if="steps.length" class="section-card zy-slide-up zy-stagger" style="--stagger-i: 6">
				<text class="section-title">创作步骤</text>
				<view class="step-timeline">
					<view
						v-for="(step, idx) in steps"
						:key="step.id || idx"
						class="step-row"
					>
						<view class="step-left">
							<view class="step-dot">
								<text class="step-dot-num">{{ step.stepNo || idx + 1 }}</text>
							</view>
							<view v-if="idx < steps.length - 1" class="step-line"></view>
						</view>
						<view class="step-content">
							<text class="step-title">{{ step.title }}</text>
							<text v-if="step.guide" class="step-guide">{{ step.guide }}</text>
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
import MediaVideo from "../../components/MediaVideo.vue"
import CraftCover from "../../components/CraftCover.vue"
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import CraftSafety from '../../components/CraftSafety.vue'
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
	return false
})

const inheritorAvatarStyle = computed(() => {
	return 'background: linear-gradient(135deg, #2f7566, #82ad9b);'
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
	return 'background: linear-gradient(135deg, #2f7566 0%, #82ad9b 100%);'
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
	background-color: var(--zy-bg, #f5f7f2);
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
	color: var(--zy-aid, #718478);
}

.error-text {
	font-size: 28rpx;
	color: var(--zy-danger, #FF6B6B);
}

/* ── 封面区 ── */
.cover-section {
	position: relative;
	height: 420rpx;
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
	height: 70%;
	background: linear-gradient(to top, rgba(31, 41, 55, 0.75) 0%, transparent 100%);
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
	text-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.35);
	font-family: var(--zy-serif);
}

/* ── 信息条 pill 标签 ── */
.info-pills {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	padding: 24rpx 24rpx 0;
}

.pill {
	display: flex;
	align-items: center;
	gap: 8rpx;
	padding: 10rpx 20rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	box-shadow: var(--zy-shadow-sm);
	border: 1rpx solid var(--zy-border, #dfe7dc);
}

.pill-text {
	font-size: 24rpx;
	color: var(--zy-content, #586e62);
}

/* ── 疗愈标签 ── */
.therapy-tags {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	padding: 16rpx 24rpx 0;
}

.therapy-tag {
	padding: 6rpx 20rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-mint, #e8efe4);
}

.therapy-tag-text {
	font-size: 22rpx;
	color: var(--zy-primary, #2f7566);
}

/* ── 传承人信息条 ── */
.inheritor-bar {
	display: flex;
	align-items: center;
	gap: 16rpx;
	margin: 24rpx;
	padding: 24rpx;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1px solid var(--zy-border, #dfe7dc);
	box-shadow: var(--zy-shadow-sm);
	transition: transform var(--zy-duration-base, 200ms) var(--zy-ease-out, ease-out);
}

.inheritor-bar:active {
	transform: scale(0.98);
}

.inheritor-avatar {
	width: 72rpx;
	height: 72rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
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
	color: var(--zy-title, #233d34);
}

.inheritor-skill {
	margin-top: 4rpx;
	font-size: 24rpx;
	color: var(--zy-aid, #718478);
}

/* ── 区块卡片 ── */
.section-card {
	position: relative;
	margin: 0 24rpx 24rpx;
	padding: 28rpx;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1px solid var(--zy-border, #dfe7dc);
	box-shadow: var(--zy-shadow-sm);
	overflow: hidden;
}

.section-quote {
	position: absolute;
	top: 4rpx;
	left: 16rpx;
	font-size: 56rpx;
	font-family: var(--zy-serif);
	color: var(--zy-primary, #2f7566);
	opacity: 0.15;
	line-height: 1;
}

.section-title {
	font-size: 32rpx;
	font-weight: 500;
	color: var(--zy-title, #233d34);
	font-family: var(--zy-serif);
	display: block;
	margin-bottom: 16rpx;
	position: relative;
}

.section-text {
	font-size: 28rpx;
	line-height: 1.9;
	color: var(--zy-content, #586e62);
	position: relative;
}

/* ── 材料网格（2 列） ── */
.material-grid {
	display: flex;
	flex-wrap: wrap;
	gap: 16rpx;
}

.material-item {
	width: calc((100% - 16rpx) / 2);
	display: flex;
	align-items: center;
	gap: 12rpx;
	padding: 16rpx 20rpx;
	border-radius: var(--zy-radius-md, 20rpx);
	background-color: var(--zy-bg-tint-mint, #e8efe4);
}

.material-icon {
	width: 48rpx;
	height: 48rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.material-name {
	flex: 1;
	font-size: 26rpx;
	color: var(--zy-title, #233d34);
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.material-qty {
	font-size: 24rpx;
	color: var(--zy-aid, #718478);
	flex-shrink: 0;
}

/* ── 步骤时间线 ── */
.step-timeline {
	display: flex;
	flex-direction: column;
}

.step-row {
	display: flex;
	gap: 20rpx;
}

.step-left {
	display: flex;
	flex-direction: column;
	align-items: center;
	flex-shrink: 0;
}

.step-dot {
	width: 52rpx;
	height: 52rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2f7566), var(--zy-primary-light, #82ad9b));
	display: flex;
	align-items: center;
	justify-content: center;
	box-shadow: 0 2rpx 8rpx rgba(45, 155, 143, 0.25);
}

.step-dot-num {
	font-size: 26rpx;
	font-weight: 600;
	color: #FFFFFF;
}

.step-line {
	width: 2rpx;
	flex: 1;
	min-height: 40rpx;
	background-color: var(--zy-border, #dfe7dc);
	margin: 4rpx 0;
}

.step-content {
	flex: 1;
	padding-bottom: 28rpx;
}

.step-title {
	font-size: 28rpx;
	font-weight: 500;
	color: var(--zy-title, #233d34);
	line-height: 1.5;
	display: block;
}

.step-guide {
	display: -webkit-box;
	margin-top: 6rpx;
	font-size: 24rpx;
	line-height: 1.6;
	color: var(--zy-aid, #718478);
	overflow: hidden;
	text-overflow: ellipsis;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
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
	background-color: var(--zy-card-bg, #FFFFFF);
	border-top: 1px solid var(--zy-border, #dfe7dc);
	z-index: 100;
}

.start-btn {
	height: 88rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2f7566), var(--zy-primary-light, #82ad9b));
	display: flex;
	align-items: center;
	justify-content: center;
	transition: transform var(--zy-duration-base, 200ms) var(--zy-ease-out, ease-out);
	box-shadow: var(--zy-shadow-lg);
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
	color: var(--zy-aid, #718478);
	text-align: center;
}
</style>
