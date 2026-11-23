<template>
	<view :class="zyThemeClass" class="page zy-page-enter">
		<!-- 加载中 -->
		<view v-if="loading" class="status-box">
			<wd-loading color="#0E5B53" />
			<text class="status-text">正在打开手作档案…</text>
		</view>

		<!-- 错误状态 -->
		<view v-else-if="errorMsg" class="status-box">
			<text class="error-text">{{ errorMsg }}</text>
			<view class="status-actions">
				<wd-button type="primary" plain size="small" @click="loadData">重试</wd-button>
				<wd-button type="primary" plain size="small" @click="goBack">返回上一页</wd-button>
			</view>
		</view>

		<!-- 项目详情：展签式 -->
		<view v-else-if="project" class="detail-content">
			<!-- 通栏材料图：标题压图，像一张展签而不是商品头图 -->
			<view class="cover zy-slide-up zy-stagger" style="--stagger-i: 0">
				<image v-if="project.coverUrl" :src="coverSrc" mode="aspectFill" class="cover-img" />
				<view v-else class="cover-pattern" :class="'ph-' + coverKind" aria-hidden="true"></view>
				<view class="cover-scrim" aria-hidden="true"></view>
				<view class="cover-top-scrim" aria-hidden="true"></view>

				<view class="cover-top">
					<AccessibleButton class="cover-fab" aria-label="返回上一页" @click="goBack">
						<ZyIcon name="back" :size="32" color="#F1F4F1" />
					</AccessibleButton>
					<text class="cover-index">手作档案 / {{ archiveNo }}</text>
					<button open-type="share" class="cover-fab" aria-label="分享手作项目" @click="share">
						<ZyIcon name="share" :size="28" color="#F1F4F1" />
					</button>
				</view>

				<view class="cover-copy">
					<text class="cover-kicker">{{ project.category || '手作' }} · {{ levelLabel(project.level) }}</text>
					<text class="cover-title zy-t-serif">{{ project.title }}</text>
					<text v-if="project.intro" class="cover-intro">{{ project.intro }}</text>
				</view>
				<text class="cover-credit">{{ coverCredit }}</text>
			</view>

			<!-- 元信息：像展签的三栏，不做 chip 流 -->
			<view class="meta-strip zy-slide-up zy-stagger" style="--stagger-i: 1">
				<view class="meta-item">
					<text class="meta-label">时间</text>
					<text class="meta-value">{{ project.estMinutes || '—' }} 分钟</text>
				</view>
				<view class="meta-item">
					<text class="meta-label">难度</text>
					<text class="meta-value">{{ levelLabel(project.level) || '未标注' }}</text>
				</view>
				<view class="meta-item">
					<text class="meta-label">技艺</text>
					<text class="meta-value">{{ project.category || '手作' }}</text>
				</view>
			</view>

			<!-- 示范者：姓名 / 技艺 / 授权与出处，缺一不可；技艺不匹配时宁可不展示，也不挂错名 -->
			<view v-if="project.inheritorProfileId && demoMatchesCraft" class="section demo-section zy-slide-up zy-stagger" style="--stagger-i: 2">
				<text class="section-kicker">示范者 / DEMONSTRATOR</text>
				<view class="demo-row">
					<text class="demo-name zy-t-serif">{{ project.inheritorNickname || '未署名示范者' }}</text>
					<text class="demo-skill">{{ project.inheritorSkill || project.category }}</text>
				</view>
				<text class="demo-source">平台邀约示范 · 项目档案来源；非官方认证课程。{{ isDemoInheritor ? '标注为“演示示范”的档案为 AI 生成演示数据。' : '示范者信息以平台核验为准。' }}</text>
				<AccessibleButton class="text-link" @click="goInheritorDetail">查看示范者档案 →</AccessibleButton>
			</view>

			<!-- 文化背景：一句大引言，不加装饰引号 -->
			<view v-if="project.cultureBg" class="quote-section zy-slide-up zy-stagger" style="--stagger-i: 3">
				<view class="quote-line" aria-hidden="true"></view>
				<text class="quote zy-t-serif">{{ project.cultureBg }}</text>
				<text class="quote-src">文化背景 · 项目档案</text>
			</view>

			<!-- 安全：未标注不开放开始 -->
			<view class="safety-section zy-slide-up zy-stagger" style="--stagger-i: 4">
				<CraftSafety :project-id="projectId" @status="onSafetyStatus" />
			</view>

			<!-- 材料：优先真实字段；缺失时给明确标注的建议清单 -->
			<view v-if="materialList.length" class="section zy-slide-up zy-stagger" style="--stagger-i: 5">
				<text class="section-kicker">工具与材料 / MATERIALS</text>
				<text class="section-title zy-t-serif">开始前，把桌面留干净</text>
				<view class="material-list">
					<view v-for="(mat, idx) in materialList" :key="mat.id || idx" class="material-row">
						<text class="material-index">{{ String(idx + 1).padStart(2, '0') }}</text>
						<text class="material-name">{{ mat.name }}</text>
						<text v-if="mat.qty" class="material-qty">{{ mat.qty }}</text>
					</view>
				</view>
				<text class="section-note">{{ materialNote }}</text>
			</view>

			<!-- 步骤：编号 + 发丝线，不做圆点气球 -->
			<view v-if="steps.length" class="section zy-slide-up zy-stagger" style="--stagger-i: 6">
				<text class="section-kicker">步骤 / STEPS</text>
				<text class="section-title zy-t-serif">跟着手，不跟着钟</text>
				<view class="steps">
					<view v-for="(step, idx) in steps" :key="step.id || idx" class="step-row" :class="{ 'is-last': idx === steps.length - 1 }">
						<view class="step-rail">
							<text class="step-num">{{ String(step.stepNo || idx + 1).padStart(2, '0') }}</text>
							<view v-if="idx !== steps.length - 1" class="step-connector" aria-hidden="true"></view>
						</view>
						<view class="step-content">
							<text class="step-title">{{ step.title }}</text>
							<text v-if="step.guide" class="step-guide">{{ step.guide }}</text>
						</view>
					</view>
				</view>
			</view>

			<view class="closing">
				<view class="closing-line" aria-hidden="true"></view>
				<text class="closing-text zy-t-serif">做完可以带走，<br/>做不完也可以留下。</text>
			</view>

			<view class="bottom-spacer"></view>
		</view>

		<!-- 引导式安全确认：不用系统蓝色弹窗，保持展签语言 -->
		<view v-if="guidedConfirmOpen" class="guided-overlay" @click="guidedConfirmOpen = false">
			<view class="guided-panel" role="dialog" aria-modal="true" aria-label="安全操作确认" @click.stop>
				<text class="guided-kicker">安全确认 / GUIDED</text>
				<text class="guided-title zy-t-serif">先在指导下确认</text>
				<text class="guided-copy">这项手作需要基础工具与带领者确认。请确认已阅读安全说明，并在可协助的环境中操作。</text>
				<view class="guided-actions">
					<button class="guided-btn guided-btn-ghost" @click="guidedConfirmOpen = false">先不开始</button>
					<button class="guided-btn guided-btn-solid" @click="confirmGuided">我已确认</button>
				</view>
			</view>
		</view>

		<!-- 底部行动：安全未标注时明确不开放 -->
		<view v-if="project" class="bottom-bar">
			<view class="bar-copy">
				<text class="bar-title">{{ canStart ? '进度自动保留，随时可以离开' : '安全标注待补，暂不开放开始' }}</text>
				<text class="bar-sub">本系统不能替代专业心理咨询</text>
			</view>
			<AccessibleButton variant="primary" size="lg" class="start-btn" :disabled="!canStart" @click="goSession">{{ canStart ? '开始创作' : '暂未开放' }}</AccessibleButton>
		</view>

		<Toast />
	</view>
</template>

<script setup>
import { useContentShare } from '../../composables/useContentShare'
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AccessibleButton from '../../components/AccessibleButton.vue'
import ZyIcon from '../../components/ZyIcon.vue'
import CraftSafety from '../../components/CraftSafety.vue'
import Toast from '../../components/Toast.vue'
import { useToast } from '../../composables/useToast'
import { http } from '../../utils/request'
import { API_BASE_URL } from '../../utils/config'

const toast = useToast()
const projectId = ref(null)
const project = ref(null)
const { share } = useContentShare('craft', () => ({ id: projectId.value, title: project.value?.title }))
const loading = ref(false)
const errorMsg = ref('')
const safetyStatus = ref('LOADING')
const guidedConfirmOpen = ref(false)

const materials = computed(() => {
	if (!project.value || !project.value.materials) return []
	return project.value.materials
})

/* 真实字段为空时的“建议清单”，明确写着以材料包为准，不冒充项目原始数据 */
const FALLBACK_MATERIALS = {
	赣绣: [{ name: '绣线' }, { name: '素布书签' }, { name: '绣针' }, { name: '小绷架' }],
	扎染: [{ name: '白棉布' }, { name: '扎染染料' }, { name: '棉线' }, { name: '手套' }],
	剪纸: [{ name: '红纸' }, { name: '剪刀' }, { name: '铅笔' }],
	陶艺: [{ name: '陶泥' }, { name: '垫板' }, { name: '塑形工具' }]
}
const fallbackMaterials = computed(() => FALLBACK_MATERIALS[project.value?.category] || [])
const materialList = computed(() => (materials.value.length ? materials.value : fallbackMaterials.value))
const materialNote = computed(() => materials.value.length
	? '材料清单来自项目档案；数量以实际材料包为准。'
	: '以上为建议清单，不是项目档案原始字段；以实际材料包为准。')

const steps = computed(() => {
	if (!project.value || !project.value.steps) return []
	return project.value.steps
})

const canStart = computed(() => safetyStatus.value === 'LOW' || safetyStatus.value === 'GUIDED')
const archiveNo = computed(() => String(project.value?.id || '').slice(-3).padStart(3, '0'))
const demoMatchesCraft = computed(() => {
	const skill = String(project.value?.inheritorSkill || '').trim()
	const category = String(project.value?.category || '').trim()
	if (!skill || !category) return false
	return skill === category
})
const isDemoInheritor = computed(() => String(project.value?.inheritorNickname || '').includes('演示示范'))
const coverCredit = computed(() => {
	const id = String(project.value?.id || '')
	if (id === '103') return '实物资料图 · 非本项目成品'
	if (id === '101') return '材料印象 · 靛蓝扎染 / AI 概念视觉，非作品实拍'
	if (id === '104') return '材料印象 · 釉与泥 / 概念视觉，非作品实拍'
	return '材料印象 · 夏布与绣线 / 概念视觉，非作品实拍'
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
		errorMsg.value = (e && e.code === 1004)
			? '这个手作已调整或暂未上架，去看看别的吧'
			: (e && e.message) || '加载失败，请稍后重试'
	} finally {
		loading.value = false
	}
}

function onSafetyStatus(level) {
	safetyStatus.value = level || 'UNKNOWN'
}

function goBack() {
	uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/home/index' }) })
}

function levelLabel(level) {
	if (level === 1) return '入门'
	if (level === 2) return '进阶'
	if (level === 3) return '高阶'
	return ''
}

function goInheritorDetail() {
	if (project.value && project.value.inheritorProfileId) {
		uni.navigateTo({ url: `/pages/inheritor/detail?id=${project.value.inheritorProfileId}` })
	} else {
		toast.show('传承人主页暂不可用')
	}
}

function goSession() {
	if (!project.value || !canStart.value) return
	if (safetyStatus.value === 'GUIDED') {
		guidedConfirmOpen.value = true
		return
	}
	enterSession()
}

function confirmGuided() {
	guidedConfirmOpen.value = false
	enterSession()
}

function enterSession() {
	if (!project.value) return
	uni.navigateTo({
		url: `/pages/craft/session?projectId=${projectId.value}&projectTitle=${encodeURIComponent(project.value.title)}`,
	})
}

const coverSrc = computed(() => {
	const src = project.value && project.value.coverUrl
	if (!src) return ''
	if (/^https?:|^\/static\//.test(src)) return src
	return API_BASE_URL + src
})
const coverKind = computed(() => {
	const cat = (project.value && (project.value.category || project.value.title)) || ''
	return /线|编|绣|染/.test(cat) ? 'yarn' : /陶|泥/.test(cat) ? 'clay' : 'paper'
})
</script>

<style scoped>
.page { min-height: 100vh; padding: 0 0 220rpx; box-sizing: border-box; background-color: var(--zy-bg); }

/* ── 状态区 ── */
.status-box { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 120rpx 0; gap: 24rpx; }
.status-text { font-size: var(--zy-text-body); color: var(--zy-text); }
.error-text { font-size: var(--zy-text-body); color: var(--zy-care); }
.status-actions { display: flex; gap: 20rpx; }

/* ── 展签封面：3:4 竖幅，标题压图 ── */
.cover { position: relative; height: 60vh; min-height: 680rpx; max-height: 960rpx; overflow: hidden; background: #0B1513; }
.cover-img { position: absolute; inset: 0; width: 100%; height: 100%; }
.cover-pattern { position: absolute; inset: 0; background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 120 120'%3E%3Cg stroke='%23ffffff' stroke-width='2' fill='none' opacity='.35'%3E%3Cpath d='M-10 30 Q30 12 60 30 T130 30'/%3E%3Cpath d='M-10 60 Q30 42 60 60 T130 60'/%3E%3Cpath d='M-10 90 Q30 72 60 90 T130 90'/%3E%3Cpath d='M30 -10 Q12 60 30 130'/%3E%3Cpath d='M60 -10 Q42 60 60 130'/%3E%3Cpath d='M90 -10 Q72 60 90 130'/%3E%3C/g%3E%3C/svg%3E"); background-size: 240rpx 240rpx; }
.cover-pattern.ph-yarn { background-color: var(--zy-brand-300); }
.cover-pattern.ph-clay { background-color: var(--zy-brand-500); }
.cover-pattern.ph-paper { background-color: var(--zy-brand-700); }
.cover-scrim { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(11,21,19,.2) 0%, rgba(11,21,19,.24) 38%, rgba(11,21,19,.82) 100%); }
.cover-top-scrim { position: absolute; top: 0; left: 0; right: 0; height: 180rpx; background: linear-gradient(180deg, rgba(11,21,19,.52) 0%, rgba(11,21,19,.16) 62%, rgba(11,21,19,0) 100%); pointer-events: none; z-index: 1; }
.cover-top { position: absolute; top: calc(26rpx + env(safe-area-inset-top, 0px)); left: 28rpx; right: 28rpx; display: flex; align-items: center; justify-content: space-between; z-index: 2; }
.cover-index { color: rgba(241,244,241,.92); font-size: 20rpx; letter-spacing: 3rpx; text-shadow: 0 1rpx 10rpx rgba(0,0,0,.52); }
.cover-fab { width: 64rpx; height: 64rpx; border: 1rpx solid rgba(241,244,241,.5); border-radius: 0; background: rgba(11,21,19,.26); display: flex; align-items: center; justify-content: center; padding: 0; margin: 0; line-height: 1; }
.cover-fab::after { border: 0; }
.cover-fab-t { font-size: 30rpx; color: var(--zy-d-text); }
.cover-copy { position: absolute; left: 36rpx; right: 36rpx; bottom: 58rpx; z-index: 1; }
.cover-kicker { display: block; font-size: 22rpx; letter-spacing: 3rpx; color: var(--zy-brand-300); }
.cover-title { display: block; font-size: 64rpx; line-height: 1.25; font-weight: 600; letter-spacing: 3rpx; color: var(--zy-d-text); margin-top: 14rpx; }
.cover-intro { display: block; margin-top: 18rpx; max-width: 90%; font-size: 24rpx; line-height: 1.8; color: rgba(241,244,241,.8); }
.cover-credit { position: absolute; left: 36rpx; right: 36rpx; bottom: 22rpx; color: rgba(241,244,241,.48); font-size: 18rpx; letter-spacing: 1rpx; }

/* ── 元信息 ── */
.meta-strip { display: grid; grid-template-columns: repeat(3, 1fr); border-bottom: 1rpx solid var(--zy-border); }
.meta-item { padding: 24rpx 28rpx 26rpx; border-right: 1rpx solid var(--zy-border); }
.meta-item:last-child { border-right: 0; }
.meta-label { display: block; color: var(--zy-muted); font-size: 18rpx; letter-spacing: 2rpx; }
.meta-value { display: block; margin-top: 8rpx; color: var(--zy-title); font-family: var(--zy-serif); font-size: 30rpx; }

/* ── 通用区块 ── */
.section { padding: 42rpx 36rpx 0; }
.section-kicker { display: block; color: var(--zy-muted); font-size: 20rpx; letter-spacing: 3rpx; }
.section-title { display: block; margin-top: 12rpx; font-size: 44rpx; line-height: 1.4; color: var(--zy-title); }

/* ── 示范者 ── */
.demo-section { padding-bottom: 36rpx; border-bottom: 1rpx solid var(--zy-border); }
.demo-row { display: flex; align-items: baseline; flex-wrap: wrap; gap: 16rpx; margin-top: 14rpx; }
.demo-name { font-size: 48rpx; color: var(--zy-title); }
.demo-skill { font-size: 22rpx; color: var(--zy-brand-700); letter-spacing: 1rpx; }
.demo-source { display: block; margin-top: 14rpx; color: var(--zy-muted); font-size: 22rpx; line-height: 1.8; }
.text-link { margin: 16rpx 0 0; padding: 0; background: transparent; color: var(--zy-brand-700); font-size: 22rpx; }

/* ── 文化背景引言 ── */
.quote-section { padding: 48rpx 36rpx 42rpx; border-bottom: 1rpx solid var(--zy-border); }
.quote-line { width: 48rpx; height: 2rpx; background: var(--zy-care); }
.quote { display: block; margin-top: 24rpx; color: var(--zy-title); font-size: 42rpx; line-height: 1.9; letter-spacing: 1rpx; }
.quote-src { display: block; margin-top: 18rpx; color: var(--zy-muted); font-size: 20rpx; letter-spacing: 2rpx; }

/* ── 安全 ── */
.safety-section { padding: 34rpx 36rpx 30rpx; border-bottom: 1rpx solid var(--zy-border); }
:deep(.safety) { margin: 0; padding: 0; background: transparent; border: 0; border-radius: 0; gap: 10rpx; color: var(--zy-text); }
:deep(.safety .heading) { font-size: 34rpx; font-weight: 600; color: var(--zy-title); font-family: var(--zy-serif); }
:deep(.safety .label) { color: var(--zy-care); font-size: 22rpx; letter-spacing: 1rpx; }
:deep(.safety .source) { color: var(--zy-muted); }

/* ── 材料 ── */
.material-list { margin-top: 24rpx; border-top: 1rpx solid var(--zy-border); }
.material-row { display: flex; align-items: baseline; gap: 18rpx; padding: 18rpx 0; border-bottom: 1rpx solid var(--zy-border); }
.material-index { width: 50rpx; flex-shrink: 0; color: var(--zy-muted); font-family: var(--zy-serif); font-size: 20rpx; }
.material-name { flex: 1; color: var(--zy-title); font-size: 30rpx; }
.material-qty { color: var(--zy-muted); font-size: 20rpx; }
.section-note { display: block; margin-top: 16rpx; color: var(--zy-muted); font-size: 20rpx; line-height: 1.7; }

/* ── 步骤 ── */
.steps { margin-top: 24rpx; }
.step-row { display: flex; gap: 20rpx; }
.step-rail { position: relative; width: 54rpx; flex-shrink: 0; display: flex; flex-direction: column; align-items: flex-start; }
.step-num { font-family: var(--zy-serif); font-size: 22rpx; color: var(--zy-muted); line-height: 1; padding-top: 4rpx; }
.step-connector { width: 1rpx; flex: 1; min-height: 70rpx; margin: 12rpx 0 0 6rpx; background: var(--zy-border); }
.step-content { flex: 1; min-width: 0; padding-bottom: 42rpx; }
.step-title { display: block; font-size: 34rpx; font-weight: 500; color: var(--zy-title); line-height: 1.5; }
.step-guide { display: block; margin-top: 8rpx; font-size: 24rpx; line-height: 1.8; color: var(--zy-text); }
.step-row.is-last .step-content { padding-bottom: 0; }

/* ── 收束句 ── */
.closing { padding: 58rpx 36rpx 0; }
.closing-line { width: 2rpx; height: 70rpx; background: var(--zy-care); }
.closing-text { display: block; margin-top: 22rpx; color: var(--zy-title); font-size: 40rpx; line-height: 1.8; letter-spacing: 1rpx; }

/* ── 引导式安全确认 ── */
.guided-overlay { position: fixed; inset: 0; z-index: 140; display: flex; align-items: center; justify-content: center; padding: 48rpx; background: rgba(11,21,19,.62); backdrop-filter: blur(4px); }
.guided-panel { width: 100%; max-width: 620rpx; padding: 40rpx 36rpx 32rpx; background: var(--zy-bg); border: 1rpx solid rgba(211,222,216,.8); box-shadow: 0 24rpx 80rpx rgba(0,0,0,.28); }
.guided-kicker { display: block; color: var(--zy-care); font-size: 20rpx; letter-spacing: 2rpx; }
.guided-title { display: block; margin-top: 16rpx; color: var(--zy-title); font-size: 46rpx; line-height: 1.3; font-weight: 500; }
.guided-copy { display: block; margin-top: 22rpx; color: var(--zy-content); font-size: 28rpx; line-height: 1.85; }
.guided-actions { display: flex; gap: 16rpx; margin-top: 32rpx; }
.guided-btn { flex: 1; height: 84rpx; line-height: 84rpx; border-radius: 0; font-size: 26rpx; padding: 0; }
.guided-btn::after { border: 0; }
.guided-btn-ghost { color: var(--zy-title); background: transparent; border: 1rpx solid var(--zy-border); }
.guided-btn-solid { color: #F1F4F1; background: var(--zy-brand-700); border: 1rpx solid var(--zy-brand-700); }

/* ── 底部行动 ── */
.bottom-bar { position: fixed; left: 0; right: 0; bottom: 0; z-index: 100; display: flex; align-items: center; justify-content: space-between; gap: 24rpx; padding: 18rpx 32rpx; padding-bottom: calc(18rpx + env(safe-area-inset-bottom, 0)); background: var(--zy-card-bg); border-top: 1rpx solid var(--zy-border); }
.bar-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.bar-title { color: var(--zy-title); font-size: 22rpx; line-height: 1.5; }
.bar-sub { color: var(--zy-muted); font-size: 18rpx; margin-top: 4rpx; }
.start-btn { width: 240rpx; flex-shrink: 0; letter-spacing: 2rpx; white-space: nowrap; }
.bottom-spacer { height: 20rpx; }
.footnote { display: none; }

button:focus-visible { outline: 3px solid var(--zy-brand-700); outline-offset: 3px; }
.section-title, .step-title, .demo-name, .cover-title, .quote, .closing-text { font-family: var(--zy-serif); font-weight: 500; }
.motion-reduced .cover, .motion-reduced .meta-strip, .motion-reduced .demo-section, .motion-reduced .quote-section, .motion-reduced .safety-section, .motion-reduced .section { animation: none !important; }
</style>
