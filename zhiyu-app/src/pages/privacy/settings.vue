<template>
	<view class="page zy-page-enter">
		<!-- 区块 1：隐私授权状态 -->
		<view class="section-card zy-slide-up zy-stagger" style="--stagger-i: 0">
			<text class="section-heading">隐私授权状态</text>
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
		<view class="section-card zy-slide-up zy-stagger" style="--stagger-i: 1">
			<text class="section-heading">可联系授权</text>
			<view class="contact-row">
				<view class="contact-info">
					<text class="contact-title">允许心理中心老师联系我</text>
					<text class="contact-desc">在必要时，学校心理中心老师可以联系你提供帮助</text>
				</view>
				<switch
					:checked="canContact === 1"
					:disabled="!isAgreed"
					color="#2D9B8F"
					@change="handleContactChange"
				/>
			</view>
			<text v-if="!isAgreed" class="contact-hint">需先同意隐私政策后才能开启</text>
		</view>

		<!-- 区块 3：危险操作 -->
		<view class="section-card section-card--danger zy-slide-up zy-stagger" style="--stagger-i: 2">
			<text class="section-heading section-heading--danger">注销账号</text>
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

onShow(() => {
	userStore.fetchPrivacyStatus()
})

const isAgreed = computed(() => userStore.privacyAgreed === 1)

const agreedLabel = computed(() =>
	userStore.privacyAgreed === 1 ? '已同意' : '未同意'
)

const agreedClass = computed(() =>
	userStore.privacyAgreed === 1 ? 'status-badge--agreed' : 'status-badge--pending'
)

const canWithdraw = computed(() => userStore.privacyAgreed === 1)
const canAgree = computed(() => userStore.privacyAgreed === 0)

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
		// 恢复开关状态
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
		// 恢复原状态
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
	background-color: var(--zy-bg, #F8F9FB);
}

.section-card {
	background-color: var(--zy-bg-warm, #FFFFFF);
	border-radius: 32rpx;
	padding: 36rpx 32rpx;
	margin-bottom: 24rpx;
	border: 1rpx solid var(--zy-border, #E5E7EB);
	box-shadow: 0 4rpx 20rpx rgba(31, 41, 55, 0.05);
}

.section-heading {
	font-size: 34rpx;
	font-weight: 600;
	color: var(--zy-title, #1F2937);
	display: block;
	margin-bottom: 20rpx;
}

.section-heading--danger {
	color: #FF6B6B;
}

.section-desc {
	display: block;
	margin-top: 16rpx;
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
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
	border-radius: 999rpx;
}

.status-badge--agreed {
	background-color: #F0F9F7;
}

.status-badge--pending {
	background-color: #FFF8EE;
}

.status-badge--agreed .status-badge-text {
	color: #2D9B8F;
	font-size: 26rpx;
}

.status-badge--pending .status-badge-text {
	color: #FFB547;
	font-size: 26rpx;
}

.btn-withdraw {
	padding: 12rpx 32rpx;
	border-radius: 999rpx;
	border: 1rpx solid #E5E7EB;
	transition: background-color 0.15s ease;
}

.btn-withdraw:active {
	background-color: #F8F9FB;
}

.btn-withdraw-text {
	font-size: 28rpx;
	color: #6B7280;
}

.btn-agree-sm {
	padding: 12rpx 32rpx;
	border-radius: 999rpx;
	background: linear-gradient(135deg, #2D9B8F, #5BC4A8);
	transition: transform 0.15s ease;
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
	color: var(--zy-title, #1F2937);
	display: block;
}

.contact-desc {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
	display: block;
	margin-top: 6rpx;
	line-height: 1.5;
}

.contact-hint {
	display: block;
	margin-top: 16rpx;
	font-size: 24rpx;
	color: #FFB547;
}

/* ── 区块 3：注销 ── */
.section-card--danger {
	border: 1rpx solid rgba(255, 107, 107, 0.15);
}

.danger-desc {
	font-size: 26rpx;
	line-height: 1.7;
	color: #6B7280;
	display: block;
	margin-bottom: 24rpx;
}

.btn-delete {
	height: 80rpx;
	border-radius: 999rpx;
	border: 1rpx solid #FF6B6B;
	display: flex;
	align-items: center;
	justify-content: center;
	transition: background-color 0.15s ease;
}

.btn-delete:active {
	background-color: rgba(255, 107, 107, 0.05);
}

.btn-delete-text {
	font-size: 30rpx;
	color: #FF6B6B;
}

/* ── 免责声明 ── */
.footnote {
	display: block;
	margin-top: 32rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
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
	background-color: #FFFFFF;
	border-radius: 24rpx;
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
	color: #1F2937;
	display: block;
	text-align: center;
	margin-bottom: 16rpx;
}

.modal-desc {
	font-size: 26rpx;
	line-height: 1.7;
	color: #6B7280;
	display: block;
	margin-bottom: 28rpx;
}

.modal-input-wrap {
	margin-bottom: 28rpx;
}

.modal-input-label {
	font-size: 26rpx;
	color: #4B5563;
	display: block;
	margin-bottom: 12rpx;
}

.modal-input {
	width: 100%;
	height: 80rpx;
	border: 1rpx solid #E5E7EB;
	border-radius: 12rpx;
	padding: 0 24rpx;
	font-size: 30rpx;
	color: #1F2937;
	box-sizing: border-box;
}

.modal-actions {
	display: flex;
	gap: 20rpx;
}

.modal-btn-cancel {
	flex: 1;
	height: 80rpx;
	border-radius: 999rpx;
	border: 1rpx solid #E5E7EB;
	display: flex;
	align-items: center;
	justify-content: center;
}

.modal-btn-cancel-text {
	font-size: 28rpx;
	color: #6B7280;
}

.modal-btn-confirm {
	flex: 1;
	height: 80rpx;
	border-radius: 999rpx;
	background-color: #FF6B6B;
	display: flex;
	align-items: center;
	justify-content: center;
	transition: opacity 0.15s ease;
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
