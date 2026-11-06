<template>
	<view class="growth-page heritage-studio-page" :class="{ 'motion-reduced': reducedMotion }">
		<view v-if="loading" class="growth-card heritage-status" role="status" aria-live="polite">
			<text class="growth-muted">正在准备配色台…</text>
		</view>
		<view v-else-if="error" class="growth-card heritage-status" role="alert">
			<text class="growth-error">{{ error }}</text>
			<AccessibleButton class="growth-button secondary" @click="load">重试</AccessibleButton>
			<AccessibleButton class="growth-button secondary" @click="back">返回纹样馆</AccessibleButton>
		</view>
		<view v-else-if="notFound" class="growth-card heritage-status" role="status" aria-live="polite">
			<text class="growth-muted">没有找到这个纹样。</text>
			<AccessibleButton class="growth-button secondary" @click="back">返回纹样馆</AccessibleButton>
		</view>
		<template v-else-if="pattern && studio">
			<view class="growth-hero studio-head">
				<text class="growth-eyebrow">HERITAGE COLOR STUDY</text>
				<text class="growth-title">{{ pattern.name }} · 配色练习</text>
				<text class="growth-subtitle">{{ pattern.traditionName }} · {{ pattern.region }} · {{ pattern.familyLabel }} · {{ pattern.level }}</text>
				<text class="heritage-origin">{{ practiceSession ? '练习会话已连接 · 作品不上传，进度会保存' : '原创教学再设计 · 不上传、不保存' }}</text>
			</view>

			<view class="studio-plan-bar" role="status" aria-live="polite">
				<text>当前结构：{{ activeStructureLabel }}</text>
				<text v-if="aiAppliedId" class="studio-plan-applied">已应用小织建议</text>
			</view>

			<view v-if="sessionId && practiceError && !practiceSession" class="growth-card heritage-status" role="alert">
				<text class="growth-error">{{ practiceError }}</text>
				<text class="growth-muted">可以继续完成当前配色练习，或返回成长旅程后重试。</text>
			</view>

			<PracticeSessionBar
				v-if="practiceSession"
				:session="practiceSession"
				:busy="practiceBusy"
				:error="practiceError"
				@pause-and-leave="pauseAndLeave"
				@open-journey="goJourney"
			/>

			<view class="studio-layout">
				<view class="studio-canvas-card" :data-primary="studio.primaryColor" :data-drawn="drawn ? 'true' : 'false'">
					<view class="studio-canvas-frame">
						<canvas
							id="heritage-studio"
							canvas-id="heritage-studio"
							class="studio-canvas"
							:style="{ width: canvasSize + 'px', height: canvasSize + 'px' }"
							role="img"
							:aria-label="canvasLabel"
							data-studio-canvas
						></canvas>
					</view>
					<text class="studio-canvas-caption">屏幕配色练习，不是实物工艺；不会保存或上传作品。</text>
				</view>

				<view class="studio-tools">
					<view class="growth-card">
						<view class="studio-step-head">
							<text class="growth-card-title">练习步骤</text>
							<text class="growth-muted">共 4 步</text>
						</view>
						<text class="studio-step-title" :data-current-step="studio.step" aria-live="polite">第 {{ studio.step }} 步 / 共 4 步 · {{ stepLabels[studio.step - 1] }}</text>
						<view class="studio-step-list">
							<view
								v-for="(label, index) in stepLabels"
								:key="label"
								:class="['studio-step', { active: studio.step === index + 1, done: studio.step > index + 1 }]"
							>
								<text class="studio-step-index">{{ studio.step > index + 1 ? '✓' : index + 1 }}</text>
								<text>{{ label }}</text>
							</view>
						</view>
						<text v-if="studio.completed" class="studio-complete" data-studio-status role="status" aria-live="polite">练习完成，未保存到账号。</text>
						<text v-else class="growth-muted studio-status" data-studio-status role="status" aria-live="polite">{{ studio.running ? '按自己的节奏，观察颜色在画面里的轻重。' : studio.started ? '已暂停，准备好后继续。' : '准备好后，开始这段数字配色练习。' }}</text>
						<AccessibleButton class="growth-button" @click="toggleRunning">{{ primaryActionLabel }}</AccessibleButton>
						<AccessibleButton v-if="!studio.completed" class="growth-button secondary" data-step-next :disabled="!canAdvance" @click="nextStep">{{ studio.step === 4 ? '完成练习' : '下一步' }}</AccessibleButton>
					</view>

					<view class="growth-card">
						<text class="growth-card-title">主色</text>
						<text class="growth-muted">换一种主色，观察同一构图的轻重变化。</text>
						<view class="studio-colors">
							<AccessibleButton
								v-for="option in colorOptions"
								:key="option.id"
								data-color
								:class="['studio-color', { selected: option.selected }]"
								:aria-pressed="option.selected"
								:aria-label="'选择' + option.name"
								@click="chooseColor(option)"
							>
								<view class="studio-color-dot" :style="{ backgroundColor: option.color }"></view>
								<text>{{ option.name }}</text>
							</AccessibleButton>
						</view>
						<AccessibleButton class="growth-button secondary" :disabled="!studio.history.length" @click="undoColor">撤销配色</AccessibleButton>
					</view>

					<view class="growth-card">
						<text class="growth-card-title">关于这张练习</text>
						<text class="growth-muted">{{ pattern.designNote }}</text>
						<text class="growth-muted studio-note">{{ practiceSession ? '本页只练习配色，作品不上传；练习进度会保存到成长旅程，不代表完成真实工艺。' : '本页只练习配色，不保存到账号，也不代表完成真实工艺。' }}</text>
					</view>
				</view>
			</view>

			<CompanionPanel
				v-if="practiceSession"
				:session="practiceSession"
				:pattern-name="pattern.name"
			/>

			<view class="growth-card studio-ai-card" data-ai-card>
				<view class="studio-step-head">
					<text class="growth-card-title">和小织一起试变体</text>
					<text class="studio-ai-badge">受控共创</text>
				</view>
				<text class="growth-muted">小织只会在本工艺的白名单结构里给 3 个变体建议，不会生成图片或代码；应用后仍可手动换色。</text>
				<view class="studio-ai-form">
					<AccessibleButton
						class="studio-consent"
						:class="{ 'is-checked': aiConsent }"
						role="checkbox"
						:aria-checked="aiConsent ? 'true' : 'false'"
						aria-label="同意本次将纹样名称与主题发送给 AI，授权不保存"
						data-ai-consent
						@click="toggleConsent"
					>
						<view class="studio-consent-box" aria-hidden="true">{{ aiConsent ? '✓' : '' }}</view>
						<text>同意本次将纹样名称与主题发送给 AI（授权不保存）</text>
					</AccessibleButton>
					<text class="studio-ai-label" id="studio-ai-theme-label">共创主题（可选）</text>
					<input
						v-model="aiTheme"
						class="studio-ai-input"
						type="text"
						maxlength="120"
						:disabled="aiBusy"
						placeholder="例如：留白与节奏"
						aria-label="共创主题，可选，最多 120 字"
						data-ai-theme
					/>
					<AccessibleButton
						class="growth-button"
						:disabled="aiBusy || !aiConsent"
						:aria-busy="aiBusy ? 'true' : 'false'"
						data-ai-submit
						@click="requestVariants"
					>{{ aiBusy ? '小织正在整理变体…' : '请小织给 3 个变体' }}</AccessibleButton>
					<text v-if="!aiConsent" class="growth-muted studio-ai-hint">勾选授权后才能发送本次请求；不勾选不会调用 AI。</text>
					<text v-if="aiError" class="growth-error studio-ai-message" role="alert" data-ai-error>{{ aiError }}</text>
					<text v-else-if="aiNote" class="growth-muted studio-ai-message" role="status" aria-live="polite" data-ai-note>{{ aiNote }}</text>
					<AccessibleButton v-if="aiAppliedId" class="growth-button secondary" data-ai-reset @click="resetVariant">恢复原纹样</AccessibleButton>
					<text class="studio-ai-disclaimer">AI 建议只在本页生效，不保存作品；不生成诊断或疗效结论。</text>
				</view>
				<view v-if="aiVariants.length" class="studio-ai-variants" aria-label="AI 变体建议">
					<view
						v-for="variant in aiVariants"
						:key="variant.id"
						:class="['studio-ai-variant', { applied: aiAppliedId === variant.id }]"
					>
						<view class="studio-ai-variant-head">
							<text class="studio-ai-variant-name">{{ variant.name }}</text>
							<text v-if="aiAppliedId === variant.id" class="studio-ai-variant-state">已应用</text>
						</view>
						<text class="studio-ai-variant-meta">{{ pattern.familyLabel }} · {{ variantLabel(variant.family, variant.variant) }} · 密度 {{ variant.detail }}</text>
						<view class="studio-ai-swatches" aria-hidden="true">
							<view v-for="(color, index) in variant.palette" :key="color + index" class="studio-ai-swatch" :style="{ backgroundColor: color }"></view>
						</view>
						<text class="studio-ai-rationale">{{ variant.rationale }}</text>
						<AccessibleButton
							class="growth-button secondary"
							:aria-pressed="aiAppliedId === variant.id ? 'true' : 'false'"
							:aria-label="'应用变体 ' + variant.name"
							@click="applyVariant(variant)"
						>{{ aiAppliedId === variant.id ? '重新应用这个变体' : '应用这个变体' }}</AccessibleButton>
					</view>
				</view>
			</view>

			<view v-if="studio.completed" class="growth-card heritage-culture-card" data-culture-card>
				<text class="growth-eyebrow">HERITAGE CULTURE CARD</text>
				<text class="culture-title">{{ pattern.name }} · 纹样文化卡</text>
				<text class="culture-meta">{{ pattern.traditionName }} · {{ pattern.region }} · {{ pattern.familyLabel }} · {{ pattern.level }}</text>
				<text class="culture-structure">本次观察：{{ activeStructureLabel }}</text>
				<view class="culture-palette" aria-label="本次完成色板">
					<view v-for="(color, index) in studio.palette" :key="color + index" class="culture-swatch">
						<view class="culture-swatch-color" :style="{ backgroundColor: color }"></view>
						<text class="culture-swatch-label">{{ ['底色', '墨色', '主色', '辅色'][index] }}</text>
					</view>
				</view>
				<view class="culture-section">
					<text class="culture-label">学习观察点</text>
					<text class="culture-text">{{ pattern.learningFocus }}</text>
				</view>
				<view class="culture-section">
					<text class="culture-label">来源背景</text>
					<text v-for="source in cultureSources" :key="source.id" class="culture-text">{{ source.title }} · {{ source.organization }}</text>
					<text v-if="!cultureSources.length" class="culture-text">目录未提供来源。</text>
				</view>
				<text class="culture-note">原创教学再设计，不是传统原作；来源仅作材料与技艺背景参考。本系统不能替代专业心理咨询。</text>
				<AccessibleButton class="growth-button" data-copy-culture @click="copyCultureCard">复制文化卡文字</AccessibleButton>
				<text v-if="copyStatus" class="culture-status" role="status" aria-live="polite">{{ copyStatus }}</text>
			</view>
			<view v-else-if="!canSaveArtifact" class="growth-card culture-teaser" role="status">
				<text class="growth-card-title">纹样文化卡</text>
				<text class="growth-muted">完成 4 步后，这里会生成本次练习的文化卡，包含纹样、工艺、地区、色板与来源，可复制保存。</text>
			</view>

			<view v-if="canSaveArtifact" class="growth-card practice-artifact-card" data-practice-artifact>
				<view class="studio-step-head">
					<view>
						<text class="growth-eyebrow">PRACTICE ARTIFACT</text>
						<text class="growth-card-title">{{ artifact ? '已保存到成长旅程' : '把这次练习留下来' }}</text>
					</view>
					<text class="studio-ai-badge">{{ artifact ? '已加密保存' : '待保存' }}</text>
				</view>
				<text class="growth-muted">保存文化卡、色板与备注，不是作品图片；会加密写入你的成长旅程，可随时删除。</text>
				<text v-if="artifactLoading" class="growth-muted">正在读取保存状态…</text>
				<view v-else class="artifact-body">
					<view class="artifact-summary">
						<text class="artifact-title">{{ (artifact && artifact.patternName) || pattern.name }} · {{ (artifact && artifact.traditionName) || pattern.traditionName }}</text>
						<text class="artifact-meta">{{ artifactStructure }} · {{ (artifact && artifact.minutesBudget) || practiceSession.minutesBudget }} 分钟</text>
						<view class="artifact-swatches" aria-label="本次色板">
							<view v-for="(color, index) in artifactPalette" :key="color + index" class="artifact-swatch" :style="{ backgroundColor: color }"></view>
						</view>
						<text class="artifact-focus">{{ (artifact && artifact.learningFocus) || pattern.learningFocus }}</text>
						<text v-if="artifact && artifact.listeningSummary" class="artifact-summary-text">倾听小结：{{ artifact.listeningSummary }}</text>
						<view v-if="artifactSources.length" class="artifact-sources">
							<text v-for="source in artifactSources" :key="source.id" class="artifact-source">{{ source.title }} · {{ source.organization }}</text>
						</view>
					</view>
					<text class="studio-ai-label">留给以后的一句话（可选）</text>
					<textarea
						v-model="artifactNotes"
						class="studio-ai-input artifact-notes"
						maxlength="500"
						:disabled="artifactBusy"
						placeholder="例如：原来慢下来，颜色会变得不一样。"
						aria-label="练习备注，可选，最多 500 字"
					/>
					<AccessibleButton
						class="studio-consent"
						:class="{ 'is-checked': artifactConsent }"
						role="checkbox"
						:aria-checked="artifactConsent ? 'true' : 'false'"
						aria-label="同意把这次练习的文化卡、色板与备注加密保存到成长旅程"
						@click="artifactConsent = !artifactConsent"
					>
						<view class="studio-consent-box" aria-hidden="true">{{ artifactConsent ? '✓' : '' }}</view>
						<text>同意把这次练习的文化卡、色板与备注加密保存到成长旅程</text>
					</AccessibleButton>
					<AccessibleButton class="growth-button" :disabled="artifactBusy || !artifactConsent" @click="saveArtifact">{{ artifactBusy ? '正在保存…' : (artifact ? '更新这次保存' : '保存到成长旅程') }}</AccessibleButton>
					<text v-if="artifactError" class="growth-error" role="alert">{{ artifactError }}</text>
					<text v-if="artifactSavedHint" class="artifact-saved-hint" role="status">{{ artifactSavedHint }}</text>
					<AccessibleButton v-if="artifact" class="growth-button secondary" :disabled="artifactBusy" @click="deleteArtifact">删除这次保存</AccessibleButton>
					<text class="studio-ai-disclaimer">删除只移除成果，不删除练习会话；可再次保存。</text>
				</view>
			</view>

			<AccessibleButton class="growth-button secondary" @click="back">返回纹样馆</AccessibleButton>
		</template>

		<text class="growth-foot">本系统不能替代专业心理咨询</text>

		<ActionSheet
			:open="deleteSheetOpen"
			title="删除这次保存"
			description="只删除练习成果，不删除练习会话。是否继续？"
			:actions="[{ key: 'delete', label: '删除这次保存', tone: 'danger', busy: artifactBusy }]"
			cancel-text="取消"
			:busy="artifactBusy"
			@select="confirmDeleteArtifact"
			@close="deleteSheetOpen = false"
		/>
		<Toast />
	</view>
</template>

<script setup>
import { computed, getCurrentInstance, nextTick, onMounted, ref, watch } from 'vue'
import { onLoad, onReady, onUnload } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import { buildHeritageGeometry } from '@/utils/heritage-pattern'
import { decoratePatterns, findHeritagePattern, sourcesForPattern, variantLabel } from '@/utils/heritage-ui'
import {
	STUDIO_COLORS,
	advanceStudio,
	applyPalette,
	completeStudio,
	createStudioState,
	drawHeritagePattern,
	pauseStudio,
	resumeStudio,
	setPrimaryColor,
	startStudio,
	undoPrimaryColor,
} from '@/utils/heritage-studio'
import { useInteraction } from '@/composables/useInteraction'
import PracticeSessionBar from '@/components/PracticeSessionBar.vue'
import CompanionPanel from '@/components/CompanionPanel.vue'
import ActionSheet from '@/components/ActionSheet.vue'
import Toast from '@/components/Toast.vue'
import AccessibleButton from '@/components/AccessibleButton.vue'
import { useToast } from '@/composables/useToast'
import { getPracticeSession, transitionPracticeSession, getPracticeArtifact, savePracticeArtifact, deletePracticeArtifact } from '@/utils/practice-session'

const { reducedMotion } = useInteraction()
const toast = useToast()
const instance = getCurrentInstance()
const loading = ref(false)
const error = ref('')
const notFound = ref(false)
const patternId = ref('')
const sessionId = ref('')
const practiceSession = ref(null)
const practiceBusy = ref(false)
const practiceError = ref('')
const artifact = ref(null)
const artifactLoading = ref(false)
const artifactBusy = ref(false)
const artifactError = ref('')
const artifactNotes = ref('')
const artifactConsent = ref(false)
const artifactSavedHint = ref('')
const deleteSheetOpen = ref(false)
const pattern = ref(null)
const catalogData = ref(null)
const activePlan = ref(null)
const studio = ref(null)
const aiConsent = ref(false)
const aiTheme = ref('')
const aiBusy = ref(false)
const aiError = ref('')
const aiNote = ref('')
const aiVariants = ref([])
const aiAppliedId = ref('')
const copyStatus = ref('')
const canvasSize = ref(300)
const pageReady = ref(false)
const drawn = ref(false)
const stepLabels = ['看结构', '试配色', '慢慢观察', '留住此刻']
let canvasContext = null
let drawTimer = null

const primaryActionLabel = computed(() => {
	if (!studio.value) return '开始练习'
	if (studio.value.completed) return '再练一次'
	if (practiceSession.value && [5, 6].includes(Number(practiceSession.value.status))) {
		return '再练一次（新练习）'
	}
	if (studio.value.running) return '暂停练习'
	return studio.value.started ? '继续练习' : '开始练习'
})
const canAdvance = computed(() => !!studio.value && studio.value.running && !studio.value.completed && studio.value.step <= 4)
const activeStructureLabel = computed(() => {
	if (!pattern.value || !activePlan.value) return ''
	return `${pattern.value.familyLabel} · ${variantLabel(activePlan.value.family, activePlan.value.variant)} · 疏密 ${activePlan.value.detail}`
})
const canSaveArtifact = computed(() => !!practiceSession.value && [5, 6].includes(Number(practiceSession.value.status)))
const artifactPalette = computed(() => {
	if (artifact.value && Array.isArray(artifact.value.palette) && artifact.value.palette.length === 4) {
		return artifact.value.palette
	}
	if (studio.value && Array.isArray(studio.value.palette)) return studio.value.palette
	return (pattern.value && pattern.value.palette) || []
})
const artifactStructure = computed(() => (artifact.value && artifact.value.structureLabel) || activeStructureLabel.value)
const artifactSources = computed(() => {
	if (artifact.value && Array.isArray(artifact.value.sources) && artifact.value.sources.length) {
		return artifact.value.sources
	}
	return cultureSources.value
})
const canvasLabel = computed(() => {
	if (!pattern.value || !studio.value || !activePlan.value) return '原创教学构图'
	const original = studio.value.originalPalette[2]
	const selected = STUDIO_COLORS.find(option => (option.value || original) === studio.value.primaryColor)
	return `${pattern.value.name} 原创教学构图，当前结构 ${activeStructureLabel.value}，主色 ${selected ? selected.name : '初色'}`
})
const cultureSources = computed(() => {
	if (!pattern.value) return []
	return sourcesForPattern(catalogData.value, pattern.value)
})
const cultureText = computed(() => {
	if (!pattern.value || !studio.value) return ''
	const sources = cultureSources.value.length
		? cultureSources.value.map(source => `${source.title}（${source.organization}）`).join('；')
		: '目录未提供来源'
	return [
		'【织愈 · 纹样文化卡】',
		`纹样：${pattern.value.name}`,
		`工艺：${pattern.value.traditionName} · ${pattern.value.region} · ${pattern.value.familyLabel}`,
		`本次观察：${activeStructureLabel.value}`,
		`色板：${studio.value.palette.join(' / ')}`,
		`学习观察点：${pattern.value.learningFocus}`,
		`来源：${sources}`,
		'说明：原创教学再设计，不是传统原作；来源仅作材料与技艺背景参考。',
		'本系统不能替代专业心理咨询。',
	].join('\n')
})
const colorOptions = computed(() => {
	if (!studio.value) return []
	const original = studio.value.originalPalette[2]
	return STUDIO_COLORS.map(option => {
		const color = option.value || original
		return { ...option, color, selected: color === studio.value.palette[2] }
	})
})

function draw() {
	if (!pageReady.value || !pattern.value || !studio.value || !activePlan.value) return
	try {
		drawn.value = false
		if (!canvasContext) canvasContext = uni.createCanvasContext('heritage-studio', instance.proxy)
		drawHeritagePattern(canvasContext, buildHeritageGeometry(activePlan.value), canvasSize.value, studio.value.palette)
		canvasContext.draw(false, () => { drawn.value = true })
	} catch (event) {
		error.value = '配色台暂时无法绘制，请返回纹样馆后重试'
	}
}

function drawLater(delay) {
	if (drawTimer) clearTimeout(drawTimer)
	drawTimer = setTimeout(draw, delay)
}

function chooseColor(option) {
	if (!studio.value) return
	studio.value = setPrimaryColor(studio.value, option.color)
}

function undoColor() {
	if (!studio.value) return
	studio.value = undoPrimaryColor(studio.value)
}

async function toggleRunning() {
	if (!studio.value || practiceBusy.value) return
	if (studio.value.completed) {
		if (practiceSession.value) {
			uni.redirectTo({ url: '/sub-heritage/pages/heritage/studio?id=' + encodeURIComponent(patternId.value) })
			return
		}
		studio.value = startStudio(studio.value)
		copyStatus.value = ''
		return
	}
	if (practiceSession.value && [5, 6].includes(Number(practiceSession.value.status))) {
		uni.redirectTo({ url: '/sub-heritage/pages/heritage/studio?id=' + encodeURIComponent(patternId.value) })
		return
	}
	if (studio.value.running) {
		if (practiceSession.value && Number(practiceSession.value.status) === 3) {
			if (!(await syncPractice('PAUSE'))) return
		}
		studio.value = pauseStudio(studio.value)
		return
	}
	if (practiceSession.value) {
		const status = Number(practiceSession.value.status)
		if (status === 4) {
			if (!(await syncPractice('RESUME'))) return
		} else if (status !== 3) {
			if (!(await syncPractice('START'))) return
		}
	}
	studio.value = studio.value.started ? resumeStudio(studio.value) : startStudio(studio.value)
}

async function nextStep() {
	if (!studio.value || !canAdvance.value || practiceBusy.value) return
	if (studio.value.step === 4 && practiceSession.value) {
		const status = Number(practiceSession.value.status)
		if (status === 3) {
			if (!(await syncPractice('COMPLETE'))) return
			await loadArtifact()
		} else if (status !== 5 && status !== 6) {
			practiceError.value = '请先继续练习，再完成本次会话。'
			return
		}
	}
	studio.value = studio.value.step === 4 ? completeStudio(studio.value) : advanceStudio(studio.value)
}

async function syncPractice(action) {
	if (!practiceSession.value) return true
	practiceBusy.value = true
	practiceError.value = ''
	try {
		practiceSession.value = await transitionPracticeSession(
			practiceSession.value.practiceSessionId,
			action
		)
		return true
	} catch (event) {
		practiceError.value = (event && event.message) || '练习进度暂时没有保存，请稍后重试'
		return false
	} finally {
		practiceBusy.value = false
	}
}

async function pauseAndLeave() {
	if (practiceBusy.value) return
	if (practiceSession.value && Number(practiceSession.value.status) === 3) {
		if (!(await syncPractice('PAUSE'))) return
		if (studio.value && studio.value.running) studio.value = pauseStudio(studio.value)
	}
	goJourney()
}

function goJourney() {
	uni.navigateTo({ url: '/pages/journey/index' })
}

async function loadArtifact() {
	if (!practiceSession.value || ![5, 6].includes(Number(practiceSession.value.status))) return
	artifactLoading.value = true
	artifactError.value = ''
	try {
		const data = await getPracticeArtifact(practiceSession.value.practiceSessionId)
		artifact.value = data && data.saved ? data : null
		artifactNotes.value = artifact.value ? (artifact.value.notes || '') : ''
	} catch (event) {
		artifactError.value = (event && event.message) || '保存状态暂时没有载入'
	} finally {
		artifactLoading.value = false
	}
}

async function saveArtifact() {
	if (!practiceSession.value || artifactBusy.value) return
	if (!artifactConsent.value) {
		artifactError.value = '请先同意保存授权'
		return
	}
	artifactBusy.value = true
	artifactError.value = ''
	artifactSavedHint.value = ''
	try {
		const data = await savePracticeArtifact(practiceSession.value.practiceSessionId, {
			consent: true,
			structureLabel: artifactStructure.value,
			notes: artifactNotes.value,
			palette: artifactPalette.value,
		})
		artifact.value = data && data.saved ? data : null
		artifactNotes.value = artifact.value ? (artifact.value.notes || '') : artifactNotes.value
		artifactConsent.value = false
		artifactSavedHint.value = '已加密保存到成长旅程。'
		practiceSession.value = await getPracticeSession(practiceSession.value.practiceSessionId)
	} catch (event) {
		artifactError.value = (event && event.message) || '保存暂时没有完成，请重试'
	} finally {
		artifactBusy.value = false
	}
}

function deleteArtifact() {
	if (!practiceSession.value || artifactBusy.value) return
	deleteSheetOpen.value = true
}

async function confirmDeleteArtifact() {
	if (!practiceSession.value || artifactBusy.value) return
	deleteSheetOpen.value = false
	artifactBusy.value = true
	artifactError.value = ''
	try {
		await deletePracticeArtifact(practiceSession.value.practiceSessionId)
		artifact.value = null
		artifactNotes.value = ''
		artifactSavedHint.value = '已删除这次保存，可重新保存。'
		practiceSession.value = await getPracticeSession(practiceSession.value.practiceSessionId)
	} catch (event) {
		artifactError.value = (event && event.message) || '删除暂时没有完成，请重试'
	} finally {
		artifactBusy.value = false
	}
}

function toggleConsent() {
	aiConsent.value = !aiConsent.value
	if (!aiConsent.value) aiError.value = ''
}

function usableVariants(value) {
	return (Array.isArray(value) ? value : []).filter(variant => {
		if (!variant || !variant.id || !variant.name || !variant.variant) return false
		if (variant.family !== pattern.value.family) return false
		if (!Number.isInteger(variant.detail) || variant.detail < 4 || variant.detail > 12) return false
		return Array.isArray(variant.palette) && variant.palette.length === 4
	}).slice(0, 3)
}

async function requestVariants() {
	if (aiBusy.value || !pattern.value) return
	if (!aiConsent.value) {
		aiError.value = '请先同意本次 AI 共创授权'
		return
	}
	aiBusy.value = true
	aiError.value = ''
	aiNote.value = ''
	aiVariants.value = []
	try {
		const data = await request({
			url: '/api/heritage/co-create',
			method: 'POST',
			data: {
				patternId: pattern.value.id,
				theme: aiTheme.value.trim(),
				consent: true,
			},
			timeout: 20000,
		})
		aiVariants.value = usableVariants(data && data.variants)
		if (data && data.message) aiNote.value = data.message
		if (!aiVariants.value.length) {
			aiError.value = '小织暂时没有返回可用变体，可以继续手动配色。'
		}
	} catch (event) {
		aiError.value = (event && event.message) || 'AI 共创暂时不可用，可以继续手动配色。'
	} finally {
		aiBusy.value = false
	}
}

function applyVariant(variant) {
	if (!studio.value || !pattern.value) return
	const palette = applyPalette(studio.value, variant.palette)
	if (palette === studio.value) return
	activePlan.value = {
		family: pattern.value.family,
		variant: variant.variant,
		detail: variant.detail,
	}
	studio.value = palette
	aiAppliedId.value = variant.id
	aiError.value = ''
	aiNote.value = `已应用“${variant.name}”的结构与色板；仍可手动换色或撤销。`
	copyStatus.value = ''
}

function resetVariant() {
	if (!studio.value || !pattern.value) return
	activePlan.value = {
		family: pattern.value.family,
		variant: pattern.value.variant,
		detail: pattern.value.detail,
	}
	studio.value = applyPalette(studio.value, pattern.value.palette)
	aiAppliedId.value = ''
	aiError.value = ''
	aiNote.value = '已恢复原纹样结构与色板。'
	copyStatus.value = ''
}

function copyCultureCard() {
	if (!cultureText.value) return
	uni.setClipboardData({
		data: cultureText.value,
		success: () => {
			copyStatus.value = '文化卡文字已复制，可粘贴到自己的学习记录。'
			toast.success('文化卡已复制')
		},
		fail: () => {
			copyStatus.value = '复制失败，请长按卡片文字手动选择。'
		},
	})
}

function back() {
	const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
	if (pages.length > 1) uni.navigateBack({ delta: 1 })
	else uni.redirectTo({ url: '/sub-heritage/pages/heritage/gallery' })
}

async function loadPracticeSession() {
	if (!sessionId.value) return
	practiceError.value = ''
	try {
		practiceSession.value = await getPracticeSession(sessionId.value)
	} catch (event) {
		practiceError.value = (event && event.message) || '练习会话暂时没有载入，可以继续完成当前配色练习'
	}
}

async function load() {
	loading.value = true
	error.value = ''
	notFound.value = false
	try {
		const data = await request({ url: '/api/heritage/catalog' })
		const found = findHeritagePattern(decoratePatterns(data), patternId.value)
		if (!found) {
			pattern.value = null
			notFound.value = true
			return
		}
		catalogData.value = data
		pattern.value = found
		activePlan.value = {
			family: found.family,
			variant: found.variant,
			detail: found.detail,
		}
		studio.value = createStudioState(found)
		aiConsent.value = false
		aiTheme.value = ''
		aiBusy.value = false
		aiError.value = ''
		aiNote.value = ''
		aiVariants.value = []
		aiAppliedId.value = ''
		copyStatus.value = ''
		await nextTick()
		draw()
		drawLater(160)
	} catch (event) {
		error.value = (event && event.message) || '配色台暂时没有载入，请重试'
	} finally {
		loading.value = false
	}
}

onLoad(async (query) => {
	patternId.value = query && query.id ? String(query.id) : ''
	sessionId.value = query && query.sessionId ? String(query.sessionId) : ''
	if (sessionId.value) await loadPracticeSession()
	if (!patternId.value && practiceSession.value && practiceSession.value.heritagePatternId) {
		patternId.value = String(practiceSession.value.heritagePatternId)
	}
	const info = uni.getSystemInfoSync()
	canvasSize.value = Math.min(Math.max(240, (info.windowWidth || 375) - 72), 520)
	await load()
	await loadArtifact()
})
onReady(() => {
	pageReady.value = true
	drawLater(0)
})
onUnload(() => {
	if (drawTimer) clearTimeout(drawTimer)
})
onMounted(() => {
	watch([() => studio.value && studio.value.palette, () => activePlan.value], () => nextTick(draw), { deep: true })
})
</script>

<style src="@/styles/growth.css"></style>
<style scoped>
.heritage-studio-page { max-width: 1180px; margin: 0 auto; }
.heritage-status { display: flex; flex-direction: column; align-items: flex-start; gap: 12rpx; }
.studio-head { padding-bottom: 8rpx; }
.heritage-origin { display: inline-block; margin-top: 14rpx; padding: 4rpx 12rpx; border-radius: 8rpx; background: var(--zy-bg-tint-mint, #e8efe4); color: var(--zy-primary, #217e76); font-size: 22rpx; }
.studio-layout { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(320px, 1fr); gap: 26rpx; align-items: start; }
.studio-canvas-card { padding: 20rpx; background: var(--zy-card-bg, #fff); border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: var(--zy-radius-lg, 32rpx); }
.studio-canvas-frame { display: flex; align-items: center; justify-content: center; padding: 18rpx; border-radius: 20rpx; background: var(--zy-bg-tint-mint, #e8efe4); }
.studio-canvas { display: block; border-radius: 12rpx; box-shadow: var(--zy-shadow-sm, 0 2rpx 8rpx rgba(33, 126, 118, .06)); }
.studio-canvas-caption { display: block; margin-top: 16rpx; font-size: 22rpx; line-height: 1.7; color: var(--zy-text, #4a5f54); }
.studio-tools { display: flex; flex-direction: column; gap: 18rpx; }
.studio-tools .growth-card { margin-bottom: 0; }
.studio-step-head { display: flex; align-items: baseline; justify-content: space-between; gap: 16rpx; }
.studio-step-title { display: block; margin: 8rpx 0 18rpx; font-size: 25rpx; color: var(--zy-title, #233d34); }
.studio-step-list { display: flex; flex-direction: column; gap: 10rpx; }
.studio-step { display: flex; align-items: center; gap: 14rpx; min-height: 56rpx; padding: 8rpx 12rpx; border-radius: 12rpx; color: var(--zy-text, #4a5f54); font-size: 24rpx; }
.studio-step.active { color: var(--zy-primary, #217e76); background: var(--zy-bg-tint-mint, #e8efe4); }
.studio-step.done { color: var(--zy-primary, #217e76); }
.studio-step-index { display: inline-flex; align-items: center; justify-content: center; width: 34rpx; height: 34rpx; flex-shrink: 0; border: 1rpx solid currentColor; border-radius: 50%; font-size: 20rpx; }
.studio-status { display: block; margin: 18rpx 0 4rpx; }
.studio-complete { display: block; margin: 18rpx 0 4rpx; padding: 14rpx 16rpx; border-radius: 12rpx; background: var(--zy-bg-tint-mint, #e8efe4); color: var(--zy-primary, #217e76); font-size: 25rpx; line-height: 1.7; }
.studio-colors { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12rpx; margin: 18rpx 0; }
.studio-color { display: flex; align-items: center; gap: 10rpx; min-height: 88rpx; margin: 0; padding: 10rpx 14rpx; text-align: left; border: 2rpx solid var(--zy-border, #dfe7dc); border-radius: 14rpx; background: var(--zy-card-bg, #fff); color: var(--zy-text, #4a5f54); font-size: 23rpx; }
.studio-color.selected { border-color: var(--zy-primary, #217e76); color: var(--zy-primary, #217e76); background: var(--zy-bg-tint-mint, #e8efe4); }
.studio-color-dot { width: 30rpx; height: 30rpx; flex-shrink: 0; border-radius: 50%; border: 1rpx solid var(--zy-border, #dfe7dc); }
.studio-note { display: block; margin-top: 10rpx; }
.studio-plan-bar { display: flex; flex-wrap: wrap; align-items: center; gap: 10rpx 16rpx; margin: 0 0 18rpx; padding: 12rpx 18rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 14rpx; background: var(--zy-card-bg, #fff); color: var(--zy-text, #4a5f54); font-size: 22rpx; line-height: 1.6; }
.studio-plan-applied { padding: 2rpx 10rpx; border-radius: 8rpx; background: var(--zy-bg-tint-mint, #e8efe4); color: var(--zy-primary, #217e76); font-size: 20rpx; }
.studio-ai-card { margin-top: 20rpx; border-color: var(--zy-border, #dfe7dc); }
.studio-ai-form { max-width: 760px; }
.studio-ai-badge { flex-shrink: 0; padding: 2rpx 10rpx; border-radius: 8rpx; background: var(--zy-bg-tint-mint, #e8efe4); color: var(--zy-primary, #217e76); font-size: 20rpx; }
.studio-consent { display: flex; align-items: flex-start; gap: 12rpx; width: 100%; margin: 16rpx 0 4rpx; padding: 14rpx 16rpx; text-align: left; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 14rpx; background: var(--zy-bg, #f5f7f2); color: var(--zy-text, #4a5f54); font-size: 23rpx; line-height: 1.6; }
.studio-consent.is-checked { border-color: var(--zy-primary, #217e76); background: var(--zy-bg-tint-mint, #e8efe4); color: var(--zy-primary, #217e76); }
.studio-consent-box { display: inline-flex; align-items: center; justify-content: center; width: 34rpx; height: 34rpx; flex-shrink: 0; margin-top: 2rpx; border: 1rpx solid currentColor; border-radius: 8rpx; font-size: 20rpx; }
.studio-ai-label { display: block; margin: 16rpx 0 0; font-size: 23rpx; color: var(--zy-title, #233d34); }
.studio-ai-input { box-sizing: border-box; width: 100%; height: 78rpx; margin: 8rpx 0 16rpx; padding: 0 18rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 12rpx; background: var(--zy-bg, #f5f7f2); color: var(--zy-title, #233d34); font-size: 24rpx; }
.studio-ai-hint { display: block; margin-top: 10rpx; }
.studio-ai-message { display: block; margin: 14rpx 0 0; line-height: 1.7; word-break: break-word; }
.studio-ai-variants { display: flex; flex-direction: column; gap: 14rpx; margin-top: 18rpx; }
.studio-ai-variant { display: flex; flex-direction: column; padding: 16rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 16rpx; background: var(--zy-bg, #f5f7f2); }
.studio-ai-variant.applied { border-color: var(--zy-primary, #217e76); background: var(--zy-bg-tint-mint, #e8efe4); }
.studio-ai-variant-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12rpx; }
.studio-ai-variant-name { font-size: 25rpx; color: var(--zy-title, #233d34); font-weight: 600; }
.studio-ai-variant-state { flex-shrink: 0; font-size: 20rpx; color: var(--zy-primary, #217e76); }
.studio-ai-variant-meta { display: block; margin-top: 4rpx; font-size: 21rpx; color: var(--zy-text, #4a5f54); }
.studio-ai-swatches { display: flex; gap: 8rpx; margin: 10rpx 0; }
.studio-ai-swatch { width: 32rpx; height: 32rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 50%; }
.studio-ai-rationale { display: block; margin-bottom: 12rpx; font-size: 22rpx; line-height: 1.7; color: var(--zy-text, #4a5f54); }
.studio-ai-variant .growth-button { margin-top: auto; }
.studio-ai-disclaimer { display: block; margin-top: 14rpx; font-size: 21rpx; line-height: 1.7; color: var(--zy-text, #4a5f54); }
.heritage-culture-card { margin-top: 20rpx; }
.culture-teaser { margin-top: 20rpx; }
.culture-title { display: block; margin-top: 6rpx; font-size: 34rpx; line-height: 1.4; color: var(--zy-title, #233d34); }
.culture-meta { display: block; margin-top: 6rpx; font-size: 23rpx; color: var(--zy-text, #4a5f54); }
.culture-structure { display: block; margin-top: 10rpx; padding: 8rpx 14rpx; border-radius: 10rpx; background: var(--zy-bg-tint-mint, #e8efe4); color: var(--zy-primary, #217e76); font-size: 22rpx; }
.culture-palette { display: flex; flex-wrap: wrap; gap: 20rpx; margin-top: 16rpx; }
.culture-swatch { display: flex; align-items: center; gap: 8rpx; }
.culture-swatch-color { width: 30rpx; height: 30rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 50%; }
.culture-swatch-label { font-size: 21rpx; color: var(--zy-text, #4a5f54); }
.culture-section { margin-top: 16rpx; }
.culture-label { display: block; font-size: 23rpx; color: var(--zy-title, #233d34); font-weight: 600; }
.culture-text { display: block; margin-top: 4rpx; font-size: 23rpx; line-height: 1.7; color: var(--zy-text, #4a5f54); word-break: break-word; }
.culture-note { display: block; margin-top: 18rpx; padding: 12rpx 16rpx; border-radius: 12rpx; background: var(--zy-bg, #f5f7f2); font-size: 21rpx; line-height: 1.7; color: var(--zy-text, #4a5f54); }
.culture-status { display: block; margin-top: 10rpx; font-size: 21rpx; line-height: 1.6; color: var(--zy-primary, #217e76); }
.practice-artifact-card { margin-top: 20rpx; }
.artifact-body { margin-top: 16rpx; }
.artifact-summary { padding: 16rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 14rpx; background: var(--zy-bg, #f5f7f2); }
.artifact-title { display: block; font-size: 27rpx; font-weight: 600; color: var(--zy-title, #233d34); }
.artifact-meta { display: block; margin-top: 4rpx; font-size: 22rpx; color: var(--zy-text, #4a5f54); }
.artifact-swatches { display: flex; gap: 10rpx; margin: 12rpx 0; }
.artifact-swatch { width: 34rpx; height: 34rpx; border-radius: 50%; border: 1rpx solid var(--zy-border, #dfe7dc); }
.artifact-focus { display: block; font-size: 22rpx; line-height: 1.7; color: var(--zy-text, #4a5f54); }
.artifact-summary-text { display: block; margin-top: 8rpx; font-size: 22rpx; line-height: 1.7; color: var(--zy-text, #4a5f54); }
.artifact-sources { margin-top: 8rpx; }
.artifact-source { display: block; font-size: 21rpx; line-height: 1.6; color: var(--zy-text, #4a5f54); }
.artifact-notes { height: 120rpx; min-height: 100rpx; }
.artifact-saved-hint { display: block; margin-top: 10rpx; font-size: 22rpx; line-height: 1.6; color: var(--zy-primary, #217e76); }
@media (min-width: 900px) { .studio-ai-variants { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 820px) { .studio-layout { grid-template-columns: minmax(0, 1fr); } }
</style>
