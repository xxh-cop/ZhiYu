<template>
	<view :class="zyThemeClass" class="page zy-page-enter">
		<!-- 加载中 -->
		<view v-if="loading" class="status-box">
			<wd-loading color="#217e76" />
			<text class="status-text">正在加载…</text>
		</view>

		<!-- 错误状态 -->
		<view v-else-if="errorMsg" class="status-box">
			<text class="error-text">{{ errorMsg }}</text>
			<view class="status-actions">
				<wd-button type="primary" plain size="small" @click="loadData">重试</wd-button>
				<wd-button type="primary" plain size="small" @click="goBack">返回上一页</wd-button>
			</view>
		</view>

		<!-- 项目详情 -->
		<view v-else-if="project" class="detail-content">
			<!-- ═══ 通栏展品封面（16:10；无真实封面时用品类纹样，不用照片冒充） ═══ -->
			<view class="cover zy-slide-up zy-stagger" style="--stagger-i: 0">
				<image v-if="project.coverUrl" :src="coverSrc" mode="aspectFill" class="cover-img" />
				<view v-else class="cover-pattern" :class="'ph-' + coverKind" aria-hidden="true"></view>
				<view class="cover-scrim" aria-hidden="true"></view>
				<button class="cover-fab cover-back" aria-label="返回上一页" @click="goBack"><text class="cover-fab-t">‹</text></button>
				<button open-type="share" class="cover-fab cover-share" aria-label="分享手作项目" @click="share"><text class="cover-fab-t">↗</text></button>
				<view class="cover-copy">
					<text class="cover-kicker">{{ project.category || '手作' }} · {{ levelLabel(project.level) }}</text>
					<text class="cover-title zy-t-serif">{{ project.title }}</text>
				</view>
			</view>

			<!-- ═══ 元信息行（传承人并入，保留跳转） ═══ -->
			<view class="metaline zy-slide-up zy-stagger" style="--stagger-i: 1">
				<text class="metaline-text">约 {{ project.estMinutes }} 分钟</text>
				<text v-if="project.inheritorProfileId" class="metaline-sep">·</text>
				<text v-if="project.inheritorProfileId" class="metaline-link" role="button" tabindex="0" @click="goInheritorDetail" @keydown.enter.prevent="goInheritorDetail" @keydown.space.prevent="goInheritorDetail">传承人 {{ project.inheritorNickname || '传承人' }} 示范</text>
			</view>

			<MediaVideo v-if="project.videoUrl" :src="project.videoUrl" caption="传承人教学视频"/>

			<!-- ═══ 疗愈标签（chip 流） ═══ -->
			<view v-if="therapyTags.length" class="chipset zy-slide-up zy-stagger" style="--stagger-i: 2">
				<view v-for="tag in therapyTags" :key="tag" class="chip">
					<text class="chip-text">{{ tag }}</text>
				</view>
			</view>

			<!-- ═══ 文化背景（引言版式：缝线竖条 + 衬线引号） ═══ -->
			<view v-if="project.cultureBg" class="pull zy-slide-up zy-stagger" style="--stagger-i: 3">
				<text class="pull-mark zy-t-serif" aria-hidden="true">“</text>
				<text class="pull-text zy-t-serif">{{ project.cultureBg }}</text>
				<text class="pull-src">文化背景 · 摘自项目档案</text>
			</view>

			<CraftSafety :project-id="projectId" />

			<!-- ═══ 所需材料（chip 流） ═══ -->
			<view v-if="materials.length" class="sec zy-slide-up zy-stagger" style="--stagger-i: 4">
				<view class="sec-head"><text class="sec-kicker">MATERIALS</text><text class="sec-title">所需材料</text></view>
				<view class="chipset">
					<view v-for="(mat, idx) in materials" :key="mat.id || idx" class="chip">
						<text class="chip-text">{{ mat.name }}<text v-if="mat.qty" class="chip-qty"> {{ mat.qty }}</text></text>
					</view>
				</view>
			</view>

			<!-- ═══ 创作步骤（缝线时间线） ═══ -->
			<view v-if="steps.length" class="sec zy-slide-up zy-stagger" style="--stagger-i: 5">
				<view class="sec-head"><text class="sec-kicker">STEPS</text><text class="sec-title">创作步骤</text></view>
				<view class="steps">
					<view v-for="(step, idx) in steps" :key="step.id || idx" class="step-row" :class="{ 'is-last': idx === steps.length - 1 }">
						<view class="step-dot" :class="{ 'is-first': idx === 0 }">
							<text class="step-dot-num">{{ step.stepNo || idx + 1 }}</text>
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
			<text class="safe-tip">进度自动保留，随时可以离开</text>
			<AccessibleButton variant="primary" size="lg" class="start-btn" @click="goSession">开始创作</AccessibleButton>
		</view>

		<!-- 底部免责声明 -->
		<text v-if="project" class="footnote">本系统不能替代专业心理咨询</text>
		<Toast />
	</view>
</template>

<script setup>
import {useContentShare} from '../../composables/useContentShare'
import MediaVideo from "../../components/MediaVideo.vue"
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AccessibleButton from '../../components/AccessibleButton.vue'
import CraftSafety from '../../components/CraftSafety.vue'
import Toast from '../../components/Toast.vue'
import { useToast } from '../../composables/useToast'
import { http } from '../../utils/request'
import { API_BASE_URL } from '../../utils/config'

const toast = useToast()

const projectId = ref(null)
const project = ref(null)
const {share}=useContentShare('craft',()=>({id:projectId.value,title:project.value?.title}))
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
		// BB（2026-11-05）：1004（项目下架/未上架/不存在，后端统一不区分）翻译为温和口径，不抛后端原文
		errorMsg.value = (e && e.code === 1004)
			? '这个手作已调整或暂未上架，去看看别的吧'
			: (e && e.message) || '加载失败，请稍后重试'
	} finally {
		loading.value = false
	}
}

// 直开无历史栈时回首页（与会话页 goBack 同款兜底）
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
		uni.navigateTo({
			url: `/pages/inheritor/detail?id=${project.value.inheritorProfileId}`,
		})
	} else {
		toast.show('传承人主页暂不可用')
	}
}

function goSession() {
	if (project.value) {
		uni.navigateTo({
			url: `/pages/craft/session?projectId=${projectId.value}&projectTitle=${encodeURIComponent(project.value.title)}`,
		})
	}
}

/* ── 暖纸青玉 P5：纯展示新增（无逻辑改动） ── */
// 真实封面地址（coverUrl → API 拼接）；无封面时封面区走纹样占位
const coverSrc = computed(() => {
	const src = project.value && project.value.coverUrl
	if (!src) return ''
	if (/^https?:|^\/static\//.test(src)) return src
	return API_BASE_URL + src
})
// 品类纹样分族（与 CraftCover kind 判定同源）
const coverKind = computed(() => {
	const cat = (project.value && (project.value.category || project.value.title)) || ''
	return /线|编|绣|染/.test(cat) ? 'yarn' : /陶|泥/.test(cat) ? 'clay' : 'paper'
})
</script>

<style scoped>
.page { min-height: 100vh; padding: 0 0 240rpx; box-sizing: border-box; background-color: var(--zy-bg, #ffffff); }

/* ── 状态区 ── */
.status-box { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 120rpx 0; gap: 24rpx; }
.status-text { font-size: var(--zy-text-body); color: var(--zy-text); }
.error-text { font-size: var(--zy-text-body); color: var(--zy-care, #a64842); }
.status-actions { display: flex; gap: 20rpx; }

/* ── 通栏封面（16:10，展品感） ── */
.cover { position: relative; height: 62.5vw; max-height: 500rpx; overflow: hidden; }
.cover-img { position: absolute; top: 0; left: 0; right: 0; bottom: 0; width: 100%; height: 100%; }
.cover-pattern { position: absolute; top: 0; left: 0; right: 0; bottom: 0; background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 120 120'%3E%3Cg stroke='%23ffffff' stroke-width='2' fill='none' opacity='.35'%3E%3Cpath d='M-10 30 Q30 12 60 30 T130 30'/%3E%3Cpath d='M-10 60 Q30 42 60 60 T130 60'/%3E%3Cpath d='M-10 90 Q30 72 60 90 T130 90'/%3E%3Cpath d='M30 -10 Q12 60 30 130'/%3E%3Cpath d='M60 -10 Q42 60 60 130'/%3E%3Cpath d='M90 -10 Q72 60 90 130'/%3E%3C/g%3E%3C/svg%3E"); background-size: 240rpx 240rpx; }
.cover-pattern.ph-yarn { background-color: var(--zy-brand-300); }
.cover-pattern.ph-clay { background-color: var(--zy-gold); }
.cover-pattern.ph-paper { background-color: var(--zy-brand-500); }
.cover-scrim { position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: linear-gradient(180deg, rgba(51,48,42,0) 34%, rgba(51,48,42,.52)); }
.cover-fab { position: absolute; top: 24rpx; width: 68rpx; height: 68rpx; border-radius: 50%; background: rgba(255,253,247,.92); display: flex; align-items: center; justify-content: center; padding: 0; margin: 0; line-height: 1; box-shadow: var(--zy-shadow-card); }
.cover-fab::after { border: 0; }
.cover-back { left: 24rpx; }
.cover-share { right: 24rpx; }
.cover-fab-t { font-size: 36rpx; color: var(--zy-title); }
.cover-copy { position: absolute; left: 32rpx; right: 32rpx; bottom: 28rpx; z-index: 1; }
.cover-kicker { display: block; font-size: var(--zy-text-micro); letter-spacing: 4rpx; color: rgba(255,255,255,.92); }
.cover-title { display: block; font-size: var(--zy-text-title-lg); font-weight: 600; letter-spacing: 2rpx; color: #FFFFFF; margin-top: 8rpx; }

/* ── 元信息行 ── */
.metaline { display: flex; align-items: baseline; flex-wrap: wrap; gap: 16rpx; padding: 28rpx 32rpx 0; }
.metaline-text { font-size: var(--zy-text-caption); color: var(--zy-text); letter-spacing: 1rpx; }
.metaline-sep { color: var(--zy-stitch); }
.metaline-link { font-size: var(--zy-text-caption); color: var(--zy-brand-700); letter-spacing: 1rpx; }

/* ── chip 流（疗愈标签 / 材料共用） ── */
.chipset { display: flex; flex-wrap: wrap; gap: 16rpx; padding: 20rpx 32rpx 0; }
.chip { background: var(--zy-card-bg); border-radius: var(--zy-radius-full); padding: 12rpx 24rpx; box-shadow: var(--zy-shadow-card); }
.chip-text { font-size: var(--zy-text-caption); color: var(--zy-text); }
.chip-qty { color: var(--zy-accent); }

/* ── 引言版式（文化背景） ── */
.pull { margin: 40rpx 32rpx 0; padding: 8rpx 0 8rpx 28rpx; border-left: 3rpx dashed var(--zy-stitch); }
.pull-mark { display: block; font-size: 52rpx; line-height: 1; color: var(--zy-gold); }
.pull-text { display: block; margin-top: 8rpx; font-size: var(--zy-text-body-lg); line-height: 2; color: var(--zy-title); }
.pull-src { display: block; margin-top: 12rpx; font-size: var(--zy-text-micro); color: var(--zy-text); letter-spacing: 1rpx; }

/* ── 区块 ── */
.sec { margin: 48rpx 32rpx 0; }
.sec-head { display: flex; align-items: baseline; gap: 16rpx; margin-bottom: 8rpx; }
.sec-kicker { font-size: var(--zy-text-micro); letter-spacing: 4rpx; color: var(--zy-accent); }
.sec-title { font-size: var(--zy-text-title); font-weight: 600; color: var(--zy-title); }
.sec .chipset { padding: 0; }

/* ── 缝线时间线 ── */
.step-row { position: relative; display: flex; gap: 24rpx; padding-bottom: 40rpx; }
.step-row.is-last { padding-bottom: 0; }
.step-dot { position: relative; z-index: 1; width: 52rpx; height: 52rpx; border-radius: 50%; border: 2rpx solid var(--zy-stitch); background: var(--zy-card-bg); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.step-dot.is-first { background: var(--zy-brand-600); border-color: var(--zy-brand-600); box-shadow: 0 0 0 10rpx rgba(33,126,118,.12); }
.step-dot-num { font-size: var(--zy-text-caption); font-weight: 600; color: var(--zy-text); }
.step-dot.is-first .step-dot-num { color: #FFFFFF; }
.step-row:not(.is-last)::before { content: ''; position: absolute; left: 25rpx; top: 56rpx; bottom: 0; border-left: 2rpx dashed var(--zy-stitch); }
.step-content { flex: 1; min-width: 0; padding-top: 4rpx; }
.step-title { display: block; font-size: var(--zy-text-body-lg); font-weight: 600; color: var(--zy-title); line-height: 1.5; }
.step-guide { display: block; margin-top: 6rpx; font-size: var(--zy-text-caption); line-height: 1.8; color: var(--zy-text); }

/* ── 底部固定按钮 ── */
.bottom-bar { position: fixed; left: 0; right: 0; bottom: 0; padding: 16rpx 32rpx; padding-bottom: calc(20rpx + env(safe-area-inset-bottom, 0)); background: var(--zy-bg); z-index: 100; }
.safe-tip { display: block; text-align: center; font-size: var(--zy-text-micro); color: var(--zy-text); margin-bottom: 12rpx; }
.start-btn { width: 100%; letter-spacing: 2rpx; }

/* ── 底部免责 ── */
.footnote { display: block; margin: 24rpx; font-size: var(--zy-text-micro); color: var(--zy-text); text-align: center; }

button:focus-visible { outline: 3px solid var(--zy-brand-700); outline-offset: 3px; }
.motion-reduced .cover, .motion-reduced .metaline, .motion-reduced .chipset, .motion-reduced .pull, .motion-reduced .sec { animation: none !important; }
</style>
