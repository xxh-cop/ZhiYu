<template>
	<view :class="zyThemeClass" class="page">
		<view v-if="loading" class="status-box">
			<ZySpinner :size="48" />
			<text class="status-text">正在打开手作档案…</text>
		</view>

		<view v-else-if="errorMsg" class="status-box">
			<text class="error-text">{{ errorMsg }}</text>
			<view class="status-actions">
				<AccessibleButton variant="secondary" size="sm" @click="loadData">重试</AccessibleButton>
				<AccessibleButton variant="secondary" size="sm" @click="goBack">返回上一页</AccessibleButton>
			</view>
		</view>

		<view v-else-if="project" class="detail-content">
			<!-- ① 大图：实拍材料图通栏，标题与朱砂印压在图上 -->
			<view class="hero">
				<image :src="heroSrc" mode="aspectFill" class="hero-img" />
				<view class="hero-shade" aria-hidden="true"></view>
				<view class="hero-cloud zy-cloud-bg" aria-hidden="true"></view>
				<view class="hero-top">
					<AccessibleButton class="glass-btn" aria-label="返回上一页" @click="goBack"><ZyIcon name="back" :size="34" color="#FFFFFF" /></AccessibleButton>
					<text class="hero-no">手作档案 · {{ archiveNo }}</text>
					<button open-type="share" class="glass-btn" aria-label="分享手作项目" @click="share"><ZyIcon name="share" :size="30" color="#FFFFFF" /></button>
				</view>
				<view class="hero-copy">
					<view class="hero-seal-row">
						<ZySeal :text="(project.category || '手作').slice(0, 2)" size="md" tilt />
						<text class="hero-kicker">{{ project.category || '手作' }}<text v-if="levelLabel(project.level)"> · {{ levelLabel(project.level) }}</text></text>
					</view>
					<text class="hero-title">{{ project.title }}</text>
					<text v-if="project.intro" class="hero-intro">{{ project.intro }}</text>
				</view>
				<text class="hero-credit">{{ coverCredit }}</text>
			</view>

			<!-- ② 浮起信息卡 -->
			<view class="info-card">
				<view class="info-item">
					<view class="info-icon tone-blue"><ZyIcon name="clock" :size="34" tone="brand" /></view>
					<text class="info-value">{{ project.estMinutes || '—' }}<text class="info-unit"> 分钟</text></text>
					<text class="info-label">预计时间</text>
				</view>
				<view class="info-divider" aria-hidden="true"></view>
				<view class="info-item">
					<view class="info-icon tone-gold"><ZyIcon name="star" :size="34" tone="gold" /></view>
					<text class="info-value">{{ levelLabel(project.level) || '未标注' }}</text>
					<text class="info-label">难度</text>
				</view>
				<view class="info-divider" aria-hidden="true"></view>
				<view class="info-item">
					<view class="info-icon tone-red"><ZyIcon name="craft" :size="34" tone="cinnabar" /></view>
					<text class="info-value">{{ project.category || '手作' }}</text>
					<text class="info-label">技艺</text>
				</view>
			</view>

			<view class="body">
				<!-- ③ 示范者 -->
				<view v-if="project.inheritorProfileId && demoMatchesCraft" class="card demo-card" role="button" tabindex="0" @click="goInheritorDetail" @keydown.enter.prevent="goInheritorDetail">
					<view class="demo-avatar"><text>{{ (project.inheritorNickname || '匠').charAt(0) }}</text></view>
					<view class="demo-copy">
						<text class="demo-kicker">示范者</text>
						<text class="demo-name">{{ project.inheritorNickname || '未署名示范者' }}</text>
						<text class="demo-skill">{{ project.inheritorSkill || project.category }} · 查看档案</text>
					</view>
					<ZyIcon name="arrow" :size="30" tone="brand" />
				</view>
				<text v-if="project.inheritorProfileId && demoMatchesCraft" class="demo-source">平台邀约示范 · 项目档案来源；非官方认证课程。{{ isDemoInheritor ? '标注为“演示示范”的档案为 AI 生成演示数据。' : '示范者信息以平台核验为准。' }}</text>

				<!-- ④ 文化背景：青花边饰引言卡 -->
				<view v-if="project.cultureBg" class="card culture-card">
					<view class="culture-band zy-band-meander" aria-hidden="true"></view>
					<text class="culture-kicker">文化背景</text>
					<text class="culture-quote">{{ project.cultureBg }}</text>
				</view>

				<!-- ⑤ 安全 -->
				<view class="safety-wrap">
					<CraftSafety :project-id="projectId" @status="onSafetyStatus" />
				</view>

				<!-- ⑥ 材料：清单 -->
				<view v-if="materialList.length" class="card">
					<view class="sec-head"><view class="sec-mark" aria-hidden="true"></view><text class="sec-title">工具与材料</text><text class="sec-note">{{ materialList.length }} 样</text></view>
					<view class="material-grid">
						<view v-for="(mat, idx) in materialList" :key="mat.id || idx" class="material-chip">
							<view class="material-check" aria-hidden="true"><ZyIcon name="check" :size="22" color="#FFFFFF" /></view>
							<text class="material-name">{{ mat.name }}</text>
							<text v-if="mat.qty" class="material-qty">{{ mat.qty }}</text>
						</view>
					</view>
					<text class="section-note">{{ materialNote }}</text>
				</view>

				<!-- ⑦ 步骤：时间线 -->
				<view v-if="steps.length" class="card">
					<view class="sec-head"><view class="sec-mark" aria-hidden="true"></view><text class="sec-title">跟着手，不跟着钟</text><text class="sec-note">{{ steps.length }} 步</text></view>
					<view class="steps">
						<view v-for="(step, idx) in steps" :key="step.id || idx" class="step-row">
							<view class="step-rail">
								<text class="step-num">{{ step.stepNo || idx + 1 }}</text>
								<view v-if="idx !== steps.length - 1" class="step-line" aria-hidden="true"></view>
							</view>
							<view class="step-content">
								<text class="step-title">{{ step.title }}</text>
								<text v-if="step.guide" class="step-guide">{{ step.guide }}</text>
							</view>
						</view>
					</view>
				</view>

				<view class="closing">
					<text class="closing-text">做完可以带走，<br/>做不完也可以留下。</text>
				</view>
			</view>
			<view class="bottom-spacer"></view>
		</view>

		<!-- 引导式安全确认 -->
		<view v-if="guidedConfirmOpen" class="guided-overlay" @click="guidedConfirmOpen = false">
			<view class="guided-panel" role="dialog" aria-modal="true" aria-label="安全操作确认" @click.stop>
				<ZySeal text="安" size="md" />
				<text class="guided-title">先在指导下确认</text>
				<text class="guided-copy">这项手作需要基础工具与带领者确认。请确认已阅读安全说明，并在可协助的环境中操作。</text>
				<view class="guided-actions">
					<AccessibleButton variant="secondary" size="md" class="guided-btn" @click="guidedConfirmOpen = false">先不开始</AccessibleButton>
					<AccessibleButton variant="primary" size="md" class="guided-btn" @click="confirmGuided">我已确认</AccessibleButton>
				</view>
			</view>
		</view>

		<!-- 底部行动栏 -->
		<view v-if="project" class="bottom-bar">
			<view class="bar-copy">
				<text class="bar-title">{{ canStart ? '进度自动保留' : '安全标注待补' }}</text>
				<text class="bar-sub">{{ canStart ? '随时可以离开，下次接着做' : '暂不开放开始' }}</text>
			</view>
			<AccessibleButton variant="primary" size="lg" class="start-cta" :disabled="!canStart" @click="goSession">{{ canStart ? '开始创作' : '暂未开放' }}</AccessibleButton>
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
import ZySeal from '../../components/ZySeal.vue'
import ZySpinner from '../../components/ZySpinner.vue'
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
/* 大图：有封面用封面；没有就按技艺取项目自带的实拍材料图，不再显示灰色占位纹 */
const HERO_BY_CATEGORY = { 赣绣: '/static/blueprint/hero-cloth.jpg', 扎染: '/static/covers/zharan.jpg', 剪纸: '/static/covers/jianzhi.jpg', 陶艺: '/static/blueprint/hero-glaze.jpg', 竹编: '/static/blueprint/hero-bamboo.jpg' }
const HERO_BY_KIND = { yarn: '/static/covers/ganxiu.jpg', clay: '/static/covers/taoyi.jpg', paper: '/static/covers/papercut-window.jpg' }
const heroSrc = computed(() => coverSrc.value || HERO_BY_CATEGORY[project.value?.category] || HERO_BY_KIND[coverKind.value])
const coverKind = computed(() => {
	const cat = (project.value && (project.value.category || project.value.title)) || ''
	return /线|编|绣|染/.test(cat) ? 'yarn' : /陶|泥/.test(cat) ? 'clay' : 'paper'
})
</script>

<style scoped>
/* v9 手作详情：实拍大图 + 浮起信息卡 + 卡片化内容 */
.page { min-height: 100vh; padding: 0 0 240rpx; box-sizing: border-box; background: var(--zy-bg); }
.status-box { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 200rpx 40rpx; gap: 24rpx; }
.status-text { font-size: 28rpx; color: var(--zy-text); }
.error-text { font-size: 28rpx; color: var(--zy-care); text-align: center; }
.status-actions { display: flex; gap: 20rpx; }

/* ① 大图 */
.hero { position: relative; height: 820rpx; overflow: hidden; background: var(--zy-brand-900); border-radius: 0 0 56rpx 56rpx; }
.hero-img { position: absolute; inset: 0; width: 100%; height: 100%; }
.hero-shade { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(13, 31, 69, .45) 0%, rgba(13, 31, 69, 0) 22%, rgba(13, 31, 69, 0) 42%, rgba(13, 31, 69, .9) 100%); }
.hero-cloud { position: absolute; right: 36rpx; bottom: 260rpx; width: 128rpx; height: 64rpx; }
.hero-top { position: absolute; left: 28rpx; right: 28rpx; top: calc(var(--status-bar-height, 0px) + 24rpx); display: flex; align-items: center; justify-content: space-between; }
.glass-btn { width: 76rpx; height: 76rpx; min-width: 76rpx; min-height: 76rpx; margin: 0; padding: 0; border-radius: 50%; background: rgba(255, 255, 255, .22); border: 2rpx solid rgba(255, 255, 255, .35); display: flex; align-items: center; justify-content: center; backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px); }
.glass-btn::after { border: 0; }
.hero-no { padding: 8rpx 20rpx; border-radius: 999rpx; background: rgba(13, 31, 69, .35); color: rgba(255, 255, 255, .9); font-size: 22rpx; letter-spacing: 2rpx; }
.hero-copy { position: absolute; left: 36rpx; right: 36rpx; bottom: 150rpx; }
.hero-seal-row { display: flex; align-items: center; gap: 16rpx; margin-bottom: 18rpx; }
.hero-kicker { padding: 6rpx 18rpx; border-radius: 999rpx; background: rgba(255, 255, 255, .2); color: #fff; font-size: 24rpx; letter-spacing: 2rpx; }
.hero-title { display: block; font-family: var(--zy-font-display); font-size: 64rpx; font-weight: 700; line-height: 1.25; letter-spacing: 4rpx; color: #fff; text-shadow: 0 4rpx 18rpx rgba(0, 0, 0, .25); }
.hero-intro { display: block; margin-top: 14rpx; font-size: 28rpx; line-height: 1.7; color: rgba(255, 255, 255, .9); }
.hero-credit { position: absolute; left: 36rpx; right: 36rpx; bottom: 104rpx; font-size: 20rpx; color: rgba(255, 255, 255, .6); }

/* ② 浮起信息卡 */
.info-card { position: relative; z-index: 2; display: flex; align-items: center; margin: -80rpx 28rpx 0; padding: 28rpx 12rpx; background: var(--zy-card-bg); border-radius: var(--zy-radius-card, 28rpx); box-shadow: var(--zy-shadow-pop); }
.info-item { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6rpx; }
.info-icon { width: 64rpx; height: 64rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin-bottom: 4rpx; }
.tone-blue { background: var(--zy-brand-50); }
.tone-gold { background: var(--zy-gamboge-50, #fbf1de); }
.tone-red { background: var(--zy-cinnabar-50, #fbecea); }
.info-value { font-family: var(--zy-font-display); font-size: 34rpx; font-weight: 700; color: var(--zy-title); }
.info-unit { font-family: var(--zy-font); font-size: 22rpx; font-weight: 400; color: var(--zy-muted); }
.info-label { font-size: 22rpx; color: var(--zy-muted); }
.info-divider { width: 2rpx; height: 88rpx; background: var(--zy-line-soft); }

/* 内容区 */
.body { padding: 28rpx 28rpx 0; }
.card { background: var(--zy-card-bg); border-radius: var(--zy-radius-card, 28rpx); box-shadow: var(--zy-shadow-card); padding: 28rpx; margin-bottom: 22rpx; }
.sec-head { display: flex; align-items: center; gap: 14rpx; margin-bottom: 20rpx; }
.sec-mark { width: 8rpx; height: 32rpx; border-radius: 4rpx; background: var(--zy-cinnabar); }
.sec-title { font-family: var(--zy-font-display); font-size: 34rpx; font-weight: 700; color: var(--zy-title); }
.sec-note { margin-left: auto; padding: 4rpx 16rpx; border-radius: 999rpx; background: var(--zy-brand-50); color: var(--zy-brand-700); font-size: 22rpx; font-weight: 600; }
.section-note { display: block; margin-top: 16rpx; font-size: 22rpx; line-height: 1.6; color: var(--zy-muted); }

/* ③ 示范者 */
.demo-card { display: flex; align-items: center; gap: 22rpx; margin-bottom: 10rpx; }
.demo-card:active { background: var(--zy-brand-50); }
.demo-avatar { width: 96rpx; height: 96rpx; border-radius: 50%; flex-shrink: 0; background: var(--zy-brand-600); color: #fff; display: flex; align-items: center; justify-content: center; font-family: var(--zy-font-display); font-size: 44rpx; font-weight: 700; box-shadow: 0 0 0 6rpx var(--zy-brand-100); }
.demo-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.demo-kicker { font-size: 22rpx; color: var(--zy-cinnabar); font-weight: 600; letter-spacing: 2rpx; }
.demo-name { font-family: var(--zy-font-display); font-size: 36rpx; font-weight: 700; color: var(--zy-title); }
.demo-skill { font-size: 24rpx; color: var(--zy-brand-600); }
.demo-source { display: block; margin: 0 8rpx 22rpx; font-size: 20rpx; line-height: 1.6; color: var(--zy-faint); }

/* ④ 文化背景 */
.culture-card { padding: 0 32rpx 32rpx; overflow: hidden; background: var(--zy-brand-50); box-shadow: none; }
.culture-band { margin: 0 -32rpx 24rpx; }
.culture-kicker { display: block; font-size: 22rpx; font-weight: 700; letter-spacing: 4rpx; color: var(--zy-brand-600); margin-bottom: 12rpx; }
.culture-quote { display: block; font-family: var(--zy-font-display); font-size: 32rpx; line-height: 1.9; color: var(--zy-brand-900); }

.safety-wrap { margin-bottom: 22rpx; }

/* ⑥ 材料 */
.material-grid { display: flex; flex-wrap: wrap; gap: 14rpx; }
.material-chip { display: flex; align-items: center; gap: 12rpx; padding: 14rpx 22rpx 14rpx 14rpx; border-radius: 999rpx; background: var(--zy-bg-sunken); }
.material-check { width: 36rpx; height: 36rpx; border-radius: 50%; background: var(--zy-pine, #2f7d68); display: flex; align-items: center; justify-content: center; }
.material-name { font-size: 26rpx; color: var(--zy-title); font-weight: 600; }
.material-qty { font-size: 22rpx; color: var(--zy-muted); }

/* ⑦ 步骤 */
.steps { display: flex; flex-direction: column; }
.step-row { display: flex; gap: 22rpx; }
.step-rail { width: 60rpx; flex-shrink: 0; display: flex; flex-direction: column; align-items: center; }
.step-num { width: 60rpx; height: 60rpx; line-height: 60rpx; text-align: center; border-radius: 50%; background: var(--zy-brand-600); color: #fff; font-family: var(--zy-font-display); font-size: 30rpx; font-weight: 700; box-shadow: 0 0 0 6rpx var(--zy-brand-100); }
.step-line { flex: 1; width: 4rpx; min-height: 28rpx; margin: 8rpx 0; border-radius: 2rpx; background: var(--zy-brand-100); }
.step-content { flex: 1; min-width: 0; padding: 8rpx 0 32rpx; }
.step-title { display: block; font-size: 30rpx; font-weight: 700; color: var(--zy-title); }
.step-guide { display: block; margin-top: 8rpx; font-size: 26rpx; line-height: 1.7; color: var(--zy-text); }

.closing { padding: 24rpx 0 8rpx; text-align: center; }
.closing-text { font-family: var(--zy-font-display); font-size: 32rpx; line-height: 1.8; color: var(--zy-brand-700); }
.bottom-spacer { height: 40rpx; }

/* 引导确认 */
.guided-overlay { position: fixed; inset: 0; z-index: 200; background: rgba(13, 31, 69, .45); display: flex; align-items: flex-end; }
.guided-panel { width: 100%; box-sizing: border-box; padding: 40rpx 36rpx calc(40rpx + env(safe-area-inset-bottom)); background: var(--zy-card-bg); border-radius: 48rpx 48rpx 0 0; display: flex; flex-direction: column; gap: 16rpx; }
.guided-title { font-family: var(--zy-font-display); font-size: 40rpx; font-weight: 700; color: var(--zy-title); }
.guided-copy { font-size: 28rpx; line-height: 1.7; color: var(--zy-text); }
.guided-actions { display: flex; gap: 16rpx; margin-top: 12rpx; }
.guided-btn { flex: 1; margin: 0; }

/* 底部行动栏：毛玻璃白 */
.bottom-bar { position: fixed; left: 0; right: 0; bottom: 0; z-index: 50; display: flex; align-items: center; gap: 20rpx; padding: 20rpx 28rpx calc(20rpx + env(safe-area-inset-bottom)); background: rgba(255, 255, 255, .88); backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px); box-shadow: 0 -8rpx 28rpx rgba(20, 36, 72, .08); }
.page.zy-theme-dark .bottom-bar { background: rgba(28, 39, 64, .92); }
.bar-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.bar-title { font-size: 28rpx; font-weight: 700; color: var(--zy-title); }
.bar-sub { font-size: 22rpx; color: var(--zy-muted); }
.start-cta { flex-shrink: 0; min-width: 260rpx; margin: 0; border-radius: 999rpx; }
</style>
