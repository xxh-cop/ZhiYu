<template>
	<view class="page">
		<scroll-view scroll-y class="scroll-area">
			<view class="policy-container">
				<!-- 顶部封面 -->
				<view class="cover zy-slide-up zy-stagger" style="--stagger-i: 0">
					<view class="cover-icon-wrap">
						<wd-icon name="setting" size="48rpx" color="#2D9B8F" />
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
					<text class="contact-value">学校心理中心 / 产品运营邮箱（上线时补充）</text>
				</view>

				<view class="bottom-spacer"></view>
			</view>
		</scroll-view>

		<!-- 底部固定按钮（仅首次同意流程显示） -->
		<view v-if="showAgreeButton" class="bottom-bar">
			<view class="btn-agree" @click="handleAgree" :class="{ 'btn-agree--loading': loading }">
				<text class="btn-agree-text">{{ loading ? '提交中...' : '我已阅读并同意' }}</text>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const loading = ref(false)
const showAgreeButton = ref(false)
const lastUpdated = ref('2024 年 12 月')

onLoad((options) => {
	if (options && options.from === 'consent') {
		showAgreeButton.value = true
	}
})

const sections = [
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
			'对话内容、心情便签、关怀提醒属敏感个人信息，应用层加密存储，解密密钥仅存服务端。',
			'咨询师与管理员看到的是脱敏后的汇总信息，不直接查看完整对话。',
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
			'查阅与复制：可在「我的-隐私」导出个人数据。',
			'更正与删除：可修改昵称、删除作品与便签；注销账号后我们将删除或匿名化你的数据。',
			'撤回同意：可随时关闭情绪对话授权，不影响浏览手作内容。',
			'注销账号：在「我的-隐私设置-注销」提交，15 个工作日内处理完成。',
		],
	},
	{
		title: '七、信息存储与保护',
		paragraphs: [
			'数据存储在学校/合作云服务的中国境内服务器。',
			'我们采取 HTTPS、加密存储、访问控制、审计日志等措施保护你的信息。',
			'保留期限：账号存续期间；注销后 30 天内删除或匿名化。',
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
		uni.showToast({ title: '已同意', icon: 'success' })
		setTimeout(() => {
			uni.navigateBack()
		}, 800)
	} catch (e) {
		uni.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
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
	background-color: var(--zy-bg, #F8F9FB);
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
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 20rpx;
}

.cover-title {
	font-size: 40rpx;
	font-weight: 600;
	color: var(--zy-title, #1F2937);
	font-family: var(--zy-serif);
}

.cover-sub {
	margin-top: 8rpx;
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
}

/* ── 免责声明 ── */
.disclaimer-box {
	background-color: var(--zy-bg-tint-warm, #FFF8EE);
	border-radius: var(--zy-radius-md, 24rpx);
	padding: 24rpx 28rpx;
	margin-bottom: 24rpx;
	border: 1rpx solid rgba(255, 181, 71, 0.2);
}

.disclaimer-text {
	font-size: 26rpx;
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
	font-size: 32rpx;
	font-weight: 600;
	color: var(--zy-primary, #2D9B8F);
	display: block;
	margin-bottom: 16rpx;
}

.para-wrap {
	margin-bottom: 12rpx;
}

.para-text {
	font-size: 28rpx;
	line-height: 1.8;
	color: var(--zy-content, #4B5563);
}

.table-row {
	display: flex;
	padding: 8rpx 0;
	border-bottom: 1rpx solid var(--zy-border, #F3F4F6);
}

.table-row:last-child {
	border-bottom: none;
}

.table-cell {
	font-size: 26rpx;
	line-height: 1.6;
	color: var(--zy-sub, #6B7280);
}

/* ── 联系卡片 ── */
.contact-card {
	background-color: var(--zy-card-bg, #FFFFFF);
	border-radius: var(--zy-radius-lg, 32rpx);
	padding: 28rpx;
	box-shadow: var(--zy-shadow-sm);
}

.contact-label {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
	display: block;
}

.contact-value {
	font-size: 26rpx;
	color: var(--zy-content, #4B5563);
	display: block;
	margin-top: 6rpx;
}

.contact-divider {
	height: 1rpx;
	background-color: var(--zy-border, #E5E7EB);
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
	border-top: 1rpx solid var(--zy-border, #E5E7EB);
}

.btn-agree {
	height: 88rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
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
	font-size: 32rpx;
	font-weight: 500;
	color: #FFFFFF;
}
</style>
