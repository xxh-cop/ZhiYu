<template>
	<view class="page zy-page-enter">
		<!-- 页面标题 -->
		<view class="page-header zy-slide-up zy-stagger" style="--stagger-i: 0">
			<text class="page-title">隐私设置</text>
			<text class="page-subtitle">管理你的隐私与授权</text>
		</view>

		<!-- 区块 1：隐私授权状态 -->
		<view class="section-card zy-slide-up zy-stagger" style="--stagger-i: 1">
			<view class="card-head">
				<view class="card-icon-wrap">
					<wd-icon name="setting" size="32rpx" color="#2f7566" />
				</view>
				<text class="section-heading">隐私授权状态</text>
			</view>
			<view class="status-row">
				<view class="status-badge" :class="agreedClass">
					<text class="status-badge-text">{{ agreedLabel }}</text>
				</view>
				<view v-if="canWithdraw" class="btn-withdraw" @click="handleWithdraw" :class="{ 'btn--loading': withdrawLoading }">
					<text class="btn-withdraw-text">{{ withdrawLoading ? '提交中...' : '撤回同意' }}</text>
				</view>
				<view v-if="canAgree" class="btn-agree-sm" @click="handleAgree" :class="{ 'btn--loading': agreeLoading }">
					<text class="btn-agree-sm-text">{{ agreeLoading ? '提交中...' : '同意隐私政策' }}</text>
				</view>
			</view>
			<text class="section-desc">撤回同意后不影响浏览手作内容，但将关闭可联系授权。</text>
		</view>

		<!-- 区块 2：可联系授权 -->
		<view class="section-card zy-slide-up zy-stagger" style="--stagger-i: 2">
			<view class="card-head">
				<view class="card-icon-wrap">
					<wd-icon name="user" size="32rpx" color="#2f7566" />
				</view>
				<text class="section-heading">可联系授权</text>
			</view>
			<view class="contact-row">
				<view class="contact-info">
					<text class="contact-title">允许心理中心老师联系我</text>
					<text class="contact-desc">在必要时，学校心理中心老师可以联系你提供帮助</text>
				</view>
				<switch
					:checked="canContact === 1"
					:disabled="!isAgreed"
					color="#2f7566"
					@change="handleContactChange"
				/>
			</view>
			<text v-if="!isAgreed" class="contact-hint">需先同意隐私政策后才能开启</text>
		</view>

		<!-- 区块 3：危险操作 -->
		<view class="section-card section-card--danger zy-slide-up zy-stagger" style="--stagger-i: 3">
			<view class="card-head">
				<view class="card-icon-wrap card-icon-danger">
					<wd-icon name="warning" size="32rpx" color="#FF6B6B" />
				</view>
				<text class="section-heading section-heading--danger">注销账号</text>
			</view>
			<text class="danger-desc">注销后账号将被禁用，作品与对话会保留但不可识别你本人。此操作不可逆。</text>
			<view class="btn-delete" @click="showDeleteModal = true">
				<text class="btn-delete-text">注销账号</text>
			</view>
		</view>

		<!-- 区块 4：底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>

		<!-- 注销确认弹窗 -->
		<view v-if="showDeleteModal" class="modal-mask" @click="showDeleteModal = false">
			<view class="modal-panel" @click.stop>
				<text class="modal-title">确认注销账号</text>
				<text class="modal-desc">注销后账号将被禁用，作品与对话会保留但不可识别你本人。此操作不可逆。</text>
				<view class="modal-input-wrap">
					<text class="modal-input-label">请输入「确认注销」四字</text>
					<input
						class="modal-input"
						v-model="deleteConfirmText"
						placeholder="确认注销"
						:maxlength="4"
					/>
				</view>
				<view class="modal-actions">
					<view class="modal-btn-cancel" @click="showDeleteModal = false">
						<text class="modal-btn-cancel-text">取消</text>
					</view>
					<view
						class="modal-btn-confirm"
						:class="{ 'modal-btn-confirm--disabled': deleteConfirmText !== '确认注销', 'modal-btn-confirm--loading': deleteLoading }"
						@click="handleDelete"
					>
						<text class="modal-btn-confirm-text">{{ deleteLoading ? '注销中...' : '确认注销' }}</text>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()

const withdrawLoading = ref(false)
const agreeLoading = ref(false)
const contactLoading = ref(false)
const showDeleteModal = ref(false)
const deleteConfirmText = ref('')
const deleteLoading = ref(false)

const isAgreed = computed(() => userStore.privacyAgreed === 1)
const canContact = computed(() => userStore.canContact || 0)

const agreedLabel = computed(() =>
	userStore.privacyAgreed === 1 ? '已同意' : '未同意'
)

const agreedClass = computed(() =>
	userStore.privacyAgreed === 1 ? 'status-badge--agreed' : 'status-badge--pending'
)

const canWithdraw = computed(() => userStore.privacyAgreed === 1)
const canAgree = computed(() => userStore.privacyAgreed === 0)

onShow(() => {
	userStore.fetchPrivacyStatus()
})

async function handleAgree() {
	if (agreeLoading.value) return
	agreeLoading.value = true
	try {
		await http.post('/api/user/privacy/agree', {})
		userStore.setPrivacyAgreed(1)
		uni.showToast({ title: '已同意', icon: 'success' })
	} catch (e) {
		uni.showToast({ title: e.message || '操作失败', icon: 'none' })
	} finally {
		agreeLoading.value = false
	}
}

async function handleWithdraw() {
	if (withdrawLoading.value) return
	uni.showModal({
		title: '撤回隐私政策同意',
		content: '撤回后将关闭可联系授权，不影响浏览手作内容。',
		confirmText: '确认撤回',
		confirmColor: '#FF6B6B',
		success: async (res) => {
			if (!res.confirm) return
			withdrawLoading.value = true
			try {
				await http.post('/api/user/privacy/withdraw', {})
				userStore.setPrivacyAgreed(0)
				userStore.setCanContact(0)
				uni.showToast({ title: '已撤回', icon: 'success' })
			} catch (e) {
				uni.showToast({ title: e.message || '操作失败', icon: 'none' })
			} finally {
				withdrawLoading.value = false
			}
		},
	})
}

async function handleContactChange(e) {
	const val = e.detail.value ? 1 : 0
	if (val === 1 && !isAgreed.value) {
		uni.showToast({ title: '请先同意隐私政策', icon: 'none' })
		userStore.setCanContact(0)
		return
	}
	if (contactLoading.value) return
	contactLoading.value = true
	try {
		await http.put('/api/user/can-contact', { canContact: val })
		userStore.setCanContact(val)
		uni.showToast({ title: val ? '已开启' : '已关闭', icon: 'success' })
	} catch (e) {
		userStore.setCanContact(val === 1 ? 0 : 1)
		uni.showToast({ title: e.message || '操作失败', icon: 'none' })
	} finally {
		contactLoading.value = false
	}
}

async function handleDelete() {
	if (deleteConfirmText.value !== '确认注销') return
	if (deleteLoading.value) return
	deleteLoading.value = true
	try {
		await http.post('/api/user/delete', { confirm: true })
		uni.showToast({ title: '账号已注销', icon: 'success' })
		setTimeout(() => {
			userStore.clearAuth()
			uni.reLaunch({ url: '/pages/login/index' })
		}, 1000)
	} catch (e) {
		uni.showToast({ title: e.message || '注销失败', icon: 'none' })
	} finally {
		deleteLoading.value = false
	}
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 32rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #f5f7f2);
}

/* ── 页面标题 ── */
.page-header {
	margin-bottom: 32rpx;
}

.page-title {
	font-size: 44rpx;
	font-weight: 500;
	color: var(--zy-title, #233d34);
	font-family: var(--zy-serif);
	display: block;
}

.page-subtitle {
	font-size: 26rpx;
	color: var(--zy-aid, #718478);
	display: block;
	margin-top: 8rpx;
}

/* ── 卡片 ── */
.section-card {
	background-color: var(--zy-card-bg, #FFFFFF);
	border-radius: var(--zy-radius-lg, 32rpx);
	padding: 32rpx 28rpx;
	margin-bottom: 20rpx;
	border: 1rpx solid var(--zy-border, #dfe7dc);
	box-shadow: var(--zy-shadow-sm);
}

.card-head {
	display: flex;
	align-items: center;
	gap: 16rpx;
	margin-bottom: 20rpx;
}

.card-icon-wrap {
	width: 56rpx;
	height: 56rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2f7566), var(--zy-primary-light, #82ad9b));
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.card-icon-danger {
	background: linear-gradient(135deg, var(--zy-danger, #FF6B6B), #FF9B9B);
}

.section-heading {
	font-size: 32rpx;
	font-weight: 600;
	color: var(--zy-title, #233d34);
}

.section-heading--danger {
	color: var(--zy-danger, #FF6B6B);
}

.section-desc {
	display: block;
	margin-top: 16rpx;
	font-size: 24rpx;
	color: var(--zy-aid, #718478);
	line-height: 1.6;
}

/* ── 区块 1：状态 ── */
.status-row {
	display: flex;
	align-items: center;
	gap: 20rpx;
}

.status-badge {
	padding: 8rpx 24rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
}

.status-badge--agreed {
	background-color: var(--zy-bg-tint-mint, #e8efe4);
}

.status-badge--pending {
	background-color: var(--zy-bg-tint-warm, #f5edde);
}

.status-badge--agreed .status-badge-text {
	color: var(--zy-primary, #2f7566);
	font-size: 26rpx;
}

.status-badge--pending .status-badge-text {
	color: var(--zy-warning, #bb914d);
	font-size: 26rpx;
}

.btn-withdraw {
	padding: 12rpx 32rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.btn-withdraw:active {
	background-color: var(--zy-bg, #f5f7f2);
}

.btn-withdraw-text {
	font-size: 28rpx;
	color: var(--zy-sub, #6B7280);
}

.btn-agree-sm {
	padding: 12rpx 32rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2f7566), var(--zy-primary-light, #82ad9b));
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.btn-agree-sm:active {
	transform: scale(0.96);
}

.btn-agree-sm-text {
	font-size: 28rpx;
	color: #FFFFFF;
}

.btn--loading {
	opacity: 0.6;
}

/* ── 区块 2：可联系 ── */
.contact-row {
	display: flex;
	align-items: center;
	gap: 20rpx;
}

.contact-info {
	flex: 1;
}

.contact-title {
	font-size: 30rpx;
	color: var(--zy-title, #233d34);
	display: block;
}

.contact-desc {
	font-size: 24rpx;
	color: var(--zy-aid, #718478);
	display: block;
	margin-top: 6rpx;
	line-height: 1.5;
}

.contact-hint {
	display: block;
	margin-top: 16rpx;
	font-size: 24rpx;
	color: var(--zy-warning, #bb914d);
}

/* ── 区块 3：注销 ── */
.section-card--danger {
	border: 1rpx solid rgba(255, 107, 107, 0.2);
}

.danger-desc {
	font-size: 26rpx;
	line-height: 1.7;
	color: var(--zy-sub, #6B7280);
	display: block;
	margin-bottom: 24rpx;
}

.btn-delete {
	height: 80rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	border: 1rpx solid var(--zy-danger, #FF6B6B);
	display: flex;
	align-items: center;
	justify-content: center;
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.btn-delete:active {
	background-color: rgba(255, 107, 107, 0.05);
}

.btn-delete-text {
	font-size: 30rpx;
	color: var(--zy-danger, #FF6B6B);
}

/* ── 免责声明 ── */
.footnote {
	display: block;
	margin-top: 32rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #718478);
	text-align: center;
}

/* ── 注销确认弹窗 ── */
.modal-mask {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: rgba(0, 0, 0, 0.5);
	z-index: 9999;
	display: flex;
	align-items: center;
	justify-content: center;
}

.modal-panel {
	width: 600rpx;
	background-color: var(--zy-card-bg, #FFFFFF);
	border-radius: var(--zy-radius-lg, 32rpx);
	padding: 40rpx 32rpx;
	animation: modalIn 0.25s ease;
}

@keyframes modalIn {
	from { opacity: 0; transform: scale(0.92); }
	to { opacity: 1; transform: scale(1); }
}

.modal-title {
	font-size: 34rpx;
	font-weight: 600;
	color: var(--zy-title, #233d34);
	display: block;
	text-align: center;
	margin-bottom: 16rpx;
}

.modal-desc {
	font-size: 26rpx;
	line-height: 1.7;
	color: var(--zy-sub, #6B7280);
	display: block;
	margin-bottom: 28rpx;
}

.modal-input-wrap {
	margin-bottom: 28rpx;
}

.modal-input-label {
	font-size: 26rpx;
	color: var(--zy-content, #586e62);
	display: block;
	margin-bottom: 12rpx;
}

.modal-input {
	width: 100%;
	height: 80rpx;
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: var(--zy-radius-sm, 12rpx);
	padding: 0 24rpx;
	font-size: 30rpx;
	color: var(--zy-title, #233d34);
	box-sizing: border-box;
}

.modal-actions {
	display: flex;
	gap: 20rpx;
}

.modal-btn-cancel {
	flex: 1;
	height: 80rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	display: flex;
	align-items: center;
	justify-content: center;
}

.modal-btn-cancel-text {
	font-size: 28rpx;
	color: var(--zy-sub, #6B7280);
}

.modal-btn-confirm {
	flex: 1;
	height: 80rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-danger, #FF6B6B);
	display: flex;
	align-items: center;
	justify-content: center;
	transition: opacity var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.modal-btn-confirm--disabled {
	opacity: 0.35;
}

.modal-btn-confirm--loading {
	opacity: 0.6;
}

.modal-btn-confirm-text {
	font-size: 28rpx;
	color: #FFFFFF;
}
</style>
