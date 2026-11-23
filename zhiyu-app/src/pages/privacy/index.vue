<template>
	<view :class="zyThemeClass" class="page">
		<scroll-view scroll-y class="scroll-area">
			<view class="policy-container">
				<!-- 顶部封面 -->
				<view class="cover zy-slide-up zy-stagger" style="--stagger-i: 0">
					<view class="cover-icon-wrap">
						<wd-icon name="setting" size="48rpx" color="#0E5B53" />
					</view>
					<text class="cover-title">隐私政策</text>
					<text class="cover-sub">我们如何保护你的数据</text>
				</view>

				<!-- 免责声明 -->
				<view class="disclaimer-box zy-slide-up zy-stagger" style="--stagger-i: 1">
					<text class="disclaimer-text">本产品不提供心理疾病诊断、治疗或医疗建议，不能替代专业心理咨询或医疗服务。</text>
				</view>

				<!-- 9 个章节 -->
				<view
					v-for="(section, idx) in sections"
					:key="idx"
					class="section-card zy-slide-up zy-stagger"
					:style="{ '--stagger-i': idx + 2 }"
				>
					<text class="section-heading">{{ section.title }}</text>
					<view v-for="(para, pi) in section.paragraphs" :key="pi" class="para-wrap">
						<text v-if="typeof para === 'string'" class="para-text">{{ para }}</text>
						<view v-else>
							<view v-for="(row, ri) in para" :key="ri" class="table-row">
								<text class="table-cell">{{ row }}</text>
							</view>
						</view>
					</view>
				</view>

				<!-- 最后更新日期 + 联系方式 -->
				<view class="contact-card zy-slide-up zy-stagger" style="--stagger-i: 11">
					<text class="contact-label">最后更新</text>
					<text class="contact-value">{{ lastUpdated }}</text>
					<view class="contact-divider"></view>
					<text class="contact-label">联系方式</text>
					<text class="contact-value">校内咨询请查看「开始 → 校内支持」。产品隐私受理联系人尚未配置，学院公开邮箱不承担本产品隐私受理。</text>
				</view>

				<view class="bottom-spacer"></view>
			</view>
		</scroll-view>

		<!-- 底部固定按钮（仅首次同意流程显示） -->
		<view v-if="showAgreeButton" class="bottom-bar">
			<view class="btn-agree" role="button" :tabindex="loading ? -1 : 0" :aria-disabled="loading" @click="handleAgree" @keydown.enter.prevent="handleAgree" @keydown.space.prevent="handleAgree" :class="{ 'btn-agree--loading': loading }">
				<text class="btn-agree-text">{{ loading ? '提交中...' : '我已阅读并同意' }}</text>
			</view>
		</view>
		<Toast />
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import { useUserStore } from '../../stores/user'
import Toast from '../../components/Toast.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()
const userStore = useUserStore()
const loading = ref(false)
const showAgreeButton = ref(false)
const lastUpdated = ref('2026 年 10 月 20 日')

onLoad((options) => {
	if (options && options.from === 'consent') {
		showAgreeButton.value = true
	}
})

const sections = [
 {title:'新增功能的数据使用',paragraphs:[
 '成长记录与自评：在单独同意后加密保存，仅本人可查阅和清除；自评计分不调用 AI。',
 'AI 辅助线稿：在单独同意后，将你输入的主题发送至当前配置的模型服务并进行安全审计；不发送绘色画面、相机画面或成长记录。生成失败时使用明确标注的内置模板。',
 '临摹辅助：仅在点击开启后请求相机权限；不请求麦克风、不录制、不上传，离开页面关闭相机。本地选图仅用于当前临摹画面。',
 '活动预约：保存报名、取消和签到状态，主办人可查看报名昵称；不向主办人提供心理自评答案。',
 '减少动效偏好保存在本机，不包含心理或身份信息。'
 ]},
	{
		title: '一、产品说明',
		paragraphs: [
			'「织愈」是一款面向在校大学生的情绪陪伴与非遗手作疗愈应用。',
			'本产品不提供心理疾病诊断、治疗或医疗建议，不能替代专业心理咨询或医疗服务。',
			'如您正在经历严重心理困扰，请立即联系学校心理中心或拨打心理援助热线。',
		],
	},
	{
		title: '二、我们收集的信息',
		paragraphs: [
			'为提供疗愈闭环服务，我们在你单独同意后收集以下信息：',
			[
				'账号信息（昵称、登录名、加密后的密码）— 注册登录 — 必要',
				'对话内容与心情便签 — 提供 AI 陪伴、生成推荐与小结 — 必要',
				'创作行为数据（步骤停留、时长、呼吸提醒响应）— 心流检测与体验优化 — 必要',
				'作品照片 — 社区分享（可匿名）— 可选',
				'设备与日志信息 — 故障排查、安全审计 — 必要',
			],
			'我们不收集：身份证号、通讯录、精确地理位置、人脸生物特征。',
		],
	},
	{
		title: '三、敏感数据处理',
		paragraphs: [
			'对话内容、心情便签及成长记录在服务端加密存储。关怀提醒仅保存脱敏摘要；AI 审计原文加密保存。',
			'情绪对话不向咨询师或管理员开放；预约交流经用户另行开启后，仅预约双方可读，关闭授权或取消预约后只读。',
			'AI 调用经过内容安全过滤，输入输出全程记录审计日志用于合规追溯。',
		],
	},
	{
		title: '四、AI 服务说明',
		paragraphs: [
			'对话由大语言模型生成，可能存在不准确或不当内容；我们已部署安全过滤，但无法完全避免。',
			'如你感到不适，可随时结束对话并联系人工资源。',
			'云端模式下，对话内容会发送至经选择的大模型服务方；本地化模式下数据不出校园。',
		],
	},
	{
		title: '五、危机干预',
		paragraphs: [
			'当系统识别到自伤、绝望等高风险表达时，将：',
			'1. 温和回应，不强行打断；',
			'2. 展示学校心理中心联系方式与全国心理援助热线；',
			'3. 提供「我需要立刻找人聊聊」一键入口；',
			'4. 以脱敏形式提醒学校心理老师进行线下跟进。',
		],
	},
	{
		title: '六、您的权利',
		paragraphs: [
			'查阅与复制：可在隐私设置分批导出基本资料、便签、对话、成长记录与反馈、作品、预约、校园核验、创作会话、学习进度、小组成员关系、本人发送的小组与预约消息、本人评论、收藏及媒体清单。文件不包含媒体文件、他人发送的消息或评论、审计日志；本人受保护图片与视频另有原文件保存入口，不是完整账号备份。',
			'更正与删除：可修改昵称、删除便签或对话、撤回作品、清除成长记录。注销将停用账号、清空个人资料和私密记录、移除本人消息及作品、取消预约与主办活动、退出小组；组长优先交接给有效成员，无成员时关闭小组。请先保存所需内容。',
			'撤回同意：可随时关闭情绪对话授权，不影响浏览手作内容。',
			'注销账号：在「我的-隐私设置」查看注销说明并输入确认文字，提交成功后账号即停用，不能恢复登录。本人受保护媒体进入持久化清理队列，删除失败会自动重试。AI 安全审计保持加密且只追加，按独立规则保留；已被他人下载的副本无法收回。',
		],
	},
	{
		title: '七、信息存储与保护',
		paragraphs: [
			'试用数据存储于当前部署服务器；正式上线前须公示实际存储地域与服务方。',
			'系统已实现应用层加密、访问控制和审计日志；正式部署需配置 HTTPS，以保护你的信息。',
			'保留期限：注销清理已接入；审计日志及备份的定期保留期限仍需运营方确定。正式部署前须落实实际保留期限与清理流程并更新本政策。',
		],
	},
	{
		title: '八、未成年人',
		paragraphs: [
			'本产品面向在校大学生（年满 18 周岁）。如你未满 18 周岁，请在监护人指导下使用。',
		],
	},
	{
		title: '九、联系我们',
		paragraphs: [
			'如对本政策有疑问，请联系：学校心理中心 / 产品运营邮箱（上线时补充）。',
		],
	},
	{
		title: '用户协议要点',
		paragraphs: [
			'1. 你承诺仅将本产品用于个人情绪调节与学习交流，不得用于违法用途。',
			'2. 社区内容须友善温暖，禁止人身攻击、广告、敏感政治内容；违规内容管理员可下架。',
			'3. 作品版权归创作者所有，发布即代表授权平台在非商业疗愈场景内展示。',
			'4. 非遗手作内容由合作传承人提供，未经授权不得商用。',
			'5. 本产品按「现状」提供，不对疗愈效果作医疗承诺。',
		],
	},
]

async function handleAgree() {
	if (loading.value) return
	loading.value = true
	try {
		await http.post('/api/user/privacy/agree', {})
		userStore.setPrivacyAgreed(1)
		toast.success('已同意')
		setTimeout(() => {
			uni.navigateBack()
		}, 800)
	} catch (e) {
		toast.care('网络异常，请稍后重试')
	} finally {
		loading.value = false
	}
}
</script>

<style scoped>
.page {
	display: flex;
	flex-direction: column;
	height: 100vh;
	background-color: var(--zy-bg, #ffffff);
}

.scroll-area {
	flex: 1;
}

.policy-container {
	padding: 32rpx 32rpx 48rpx;
}

/* ── 顶部封面 ── */
.cover {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 32rpx 0 24rpx;
	margin-bottom: 24rpx;
}

.cover-icon-wrap {
	width: 96rpx;
	height: 96rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-mint, #e8efe4);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 20rpx;
}

.cover-title {
	font-size:var(--zy-text-title-lg, 40rpx);
	font-weight: 600;
	color: var(--zy-title, #233d34);
	font-family: var(--zy-serif);
}

.cover-sub {
	margin-top: 8rpx;
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-text, #4a5f54);
}

/* ── 免责声明 ── */
.disclaimer-box {
	background-color: var(--zy-bg-tint-warm, #f7efd9);
	border-radius: var(--zy-radius-md, 24rpx);
	padding: 24rpx 28rpx;
	margin-bottom: 24rpx;
	border: 1rpx solid rgba(217,164,65,.2);
}

.disclaimer-text {
	font-size:var(--zy-text-caption, 24rpx);
	line-height: 1.7;
	color: var(--zy-warning, #92400E);
}

/* ── 章节卡片 ── */
.section-card {
	background-color: var(--zy-card-bg, #FFFFFF);
	border-radius: var(--zy-radius-lg, 32rpx);
	padding: 32rpx 28rpx;
	margin-bottom: 20rpx;
	box-shadow: var(--zy-shadow-sm);
}

.section-heading {
	font-size:var(--zy-text-title, 32rpx);
	font-weight: 600;
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
	display: block;
	margin-bottom: 16rpx;
}

.para-wrap {
	margin-bottom: 12rpx;
}

.para-text {
	font-size:var(--zy-text-body, 28rpx);
	line-height: 1.8;
	color: var(--zy-content, #4a5f54);
}

.table-row {
	display: flex;
	padding: 8rpx 0;
	border-bottom: 1rpx solid var(--zy-border, #dfe7dc);
}

.table-row:last-child {
	border-bottom: none;
}

.table-cell {
	font-size:var(--zy-text-caption, 24rpx);
	line-height: 1.6;
	color: var(--zy-text, #4a5f54);
}

/* ── 联系卡片 ── */
.contact-card {
	background-color: var(--zy-card-bg, #FFFFFF);
	border-radius: var(--zy-radius-lg, 32rpx);
	padding: 28rpx;
	box-shadow: var(--zy-shadow-sm);
}

.contact-label {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-text, #4a5f54);
	display: block;
}

.contact-value {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-content, #4a5f54);
	display: block;
	margin-top: 6rpx;
}

.contact-divider {
	height: 1rpx;
	background-color: var(--zy-border, #dfe7dc);
	margin: 20rpx 0;
}

.bottom-spacer {
	height: 40rpx;
}

/* ── 底部固定按钮 ── */
.bottom-bar {
	padding: 20rpx 32rpx;
	padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
	background-color: var(--zy-card-bg, #FFFFFF);
	border-top: 1rpx solid var(--zy-border, #dfe7dc);
}

.btn-agree {
	height: 88rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-brand-600, var(--zy-brand-600, #217e76));
	display: flex;
	align-items: center;
	justify-content: center;
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
	box-shadow: var(--zy-shadow-md);
}

.btn-agree:active {
	transform: scale(0.97);
}

.btn-agree--loading {
	opacity: 0.6;
}

.btn-agree-text {
	font-size:var(--zy-text-title, 32rpx);
	font-weight: 500;
	color: #FFFFFF;
}

button:focus-visible {
	outline: 3px solid var(--zy-brand-700, var(--zy-brand-700, #1a645e));
	outline-offset: 3px;
}
</style>
