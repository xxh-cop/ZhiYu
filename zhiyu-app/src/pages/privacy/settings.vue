<template>
  <view class="privacy-page">
    <view :aria-hidden="!!dialogMode">
      <view class="privacy-hero">
        <view><text class="hero-label">账号与隐私</text><text class="hero-title">你的记录，由你做主</text><text class="hero-description">查看授权、保存副本，再决定如何管理自己的账号。</text></view>
        <image src="/static/xiaozhi-caring.png" mode="aspectFit" aria-label="小织陪你管理个人记录" />
      </view>
      <view v-if="statusLoading" class="status-banner" role="status">正在核对最新授权状态…</view>
      <view v-if="statusError" class="status-banner error-banner" role="alert"><text>{{ statusError }}</text><AccessibleButton class="text-button" @click="loadStatus">重新加载</AccessibleButton></view>
      <view v-if="actionError" class="status-banner error-banner" role="alert">{{ actionError }}</view>
      <view class="privacy-grid">
        <view class="authorization-card card">
          <text class="section-label">01 / 授权管理</text><text class="section-title">决定如何使用你的信息</text>
          <view class="permission-block">
            <view class="permission-heading"><text class="permission-title">隐私政策同意</text><text class="status-tag" :class="{ enabled: ready && isAgreed }">{{ !ready ? '待确认' : isAgreed ? '已同意' : '未同意' }}</text></view>
            <text class="body-text">用于需要授权的情绪记录与陪伴功能。撤回后关闭可联系授权，已有记录仍可导出。</text>
            <view class="button-row">
              <AccessibleButton class="text-button" :disabled="busy" @click="readPolicy">阅读隐私政策 ↗</AccessibleButton>
              <AccessibleButton v-if="isAgreed" class="secondary-button" :disabled="!ready || busy" @click="openDialog('withdraw')">撤回同意</AccessibleButton>
              <AccessibleButton v-else class="primary-button" :disabled="!ready || busy" :loading="operation === 'agree'" @click="agree">同意隐私政策</AccessibleButton>
            </view>
          </view>
          <view class="permission-block">
            <view class="permission-heading"><text class="permission-title">允许老师联系我</text><text class="status-tag" :class="{ enabled: ready && canContact }">{{ !ready ? '待确认' : canContact ? '已开启' : '已关闭' }}</text></view>
            <text class="body-text">单独决定是否允许授权老师在必要时联系你。此项不会随同意隐私政策自动开启。</text>
            <AccessibleButton class="contact-button" :class="{ enabled: canContact }" :aria-pressed="canContact" :disabled="!ready || !isAgreed || busy"
              :loading="operation === 'contact'" @click="toggleContact">{{ operation === 'contact' ? '正在更新…' : canContact ? '关闭联系授权' : '允许老师联系我' }}</AccessibleButton>
            <text v-if="ready && !isAgreed" class="help-text">同意隐私政策后可自主开启。</text>
          </view>
          <view class="authorization-note"><text>以最新状态为准</text><text>操作成功后会更新本页。加载失败时暂停修改，你可以重新加载。</text></view>
        </view>
        <PersonalDataExport :disabled="!!operation" @busy-change="exporting = $event" />
      </view>
      <view class="account-card card">
        <view><text class="section-label">03 / 账号管理</text><text class="section-title">离开之前，先保存需要的记录</text><text class="body-text">{{ deletionNotice }}</text></view>
        <AccessibleButton class="danger-button" :disabled="!ready || busy" @click="openDialog('delete')">查看注销说明</AccessibleButton>
      </view>
      <text class="footnote">本系统不能替代专业心理咨询</text>
    </view>
    <AccessibleDialog :open="!!dialogMode" :busy="!!operation" :title="dialogMode === 'delete' ? '确认注销账号' : '撤回隐私政策同意'" @close="closeDialog">
      <text class="dialog-description">{{ dialogMode === 'delete' ? deletionNotice : '撤回后将关闭可联系授权，暂停需要同意的记录与陪伴功能。仍可浏览手作内容、导出已有记录，之后也可以重新同意。' }}</text>
      <view v-if="dialogMode === 'delete'" class="confirmation-field">
        <label for="delete-confirm"><text class="field-label">请输入“确认注销”</text></label>
        <input id="delete-confirm" v-model="deleteText" class="confirmation-input" aria-label="请输入确认注销四字" :maxlength="4" :disabled="!!operation" placeholder="确认注销" />
      </view>
      <text v-if="dialogError" class="dialog-error" role="alert">{{ dialogError }}</text>
      <view class="dialog-actions">
        <AccessibleButton class="secondary-button" :disabled="!!operation" @click="closeDialog">保留当前设置</AccessibleButton>
        <AccessibleButton class="danger-solid" :loading="!!operation" :disabled="dialogMode === 'delete' && deleteText !== '确认注销'" @click="confirmDialog">{{ operation ? '正在提交…' : dialogMode === 'delete' ? '确认注销' : '确认撤回' }}</AccessibleButton>
      </view>
    </AccessibleDialog>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import { useUserStore } from '../../stores/user'
import PersonalDataExport from '../../components/PersonalDataExport.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import AccessibleDialog from '../../components/AccessibleDialog.vue'
const userStore = useUserStore()
const deletionNotice = '注销后无法恢复登录。系统将清除个人资料、私密记录和本人消息，撤下作品、取消预约与主办活动，并退出小组。本人受保护媒体会进入后台删除队列，失败时重试；安全审计按独立规则保留，已被他人下载的副本无法收回。请先导出记录、保存原文件。'
const ready = ref(false), statusLoading = ref(false), statusError = ref(''), actionError = ref('')
const operation = ref(''), exporting = ref(false), dialogMode = ref(''), dialogError = ref(''), deleteText = ref('')
const isAgreed = computed(() => userStore.privacyAgreed === 1)
const canContact = computed(() => userStore.canContact === 1)
const busy = computed(() => !!operation.value || exporting.value || statusLoading.value)
async function loadStatus() {
  if (statusLoading.value || operation.value) return
  ready.value = false; statusLoading.value = true; statusError.value = ''
  try {
    const status = await http.get('/api/user/privacy/status')
    userStore.setPrivacyAgreed(status.privacyAgreed); userStore.setCanContact(status.canContact)
    ready.value = true
  } catch (e) { statusError.value = e.message || '暂时无法获取授权状态，请重试' }
  finally { statusLoading.value = false }
}
function readPolicy() { uni.navigateTo({ url: '/pages/privacy/index' }) }
function openDialog(mode) {
  if (!ready.value || busy.value) return
  deleteText.value = ''; dialogError.value = ''; dialogMode.value = mode
}
function closeDialog() {
  if (operation.value) return
  dialogMode.value = ''; deleteText.value = ''; dialogError.value = ''
}
async function agree() {
  if (!ready.value || busy.value) return
  operation.value = 'agree'; actionError.value = ''
  try {
    await http.post('/api/user/privacy/agree', {})
    userStore.setPrivacyAgreed(1)
    uni.showToast({ title: '已同意', icon: 'success' })
  } catch (e) { actionError.value = e.message || '未能更新授权，请重试' }
  finally { operation.value = '' }
}
async function toggleContact() {
  if (!ready.value || busy.value || !isAgreed.value) return
  const value = canContact.value ? 0 : 1
  operation.value = 'contact'; actionError.value = ''
  try {
    await http.put('/api/user/can-contact', { canContact: value })
    userStore.setCanContact(value)
    uni.showToast({ title: value ? '已开启联系授权' : '已关闭联系授权', icon: 'none' })
  } catch (e) { actionError.value = e.message || '未能更新联系授权，原设置保持不变' }
  finally { operation.value = '' }
}
async function confirmDialog() {
  if (busy.value || !dialogMode.value || (dialogMode.value === 'delete' && deleteText.value !== '确认注销')) return
  const mode = dialogMode.value
  operation.value = mode; dialogError.value = ''
  try {
    if (mode === 'delete') {
      await http.post('/api/user/delete', { confirm: true })
      userStore.clearAuth()
      uni.reLaunch({ url: '/pages/login/index' })
    } else {
      await http.post('/api/user/privacy/withdraw', {})
      userStore.setPrivacyAgreed(0); userStore.setCanContact(0)
      uni.showToast({ title: '已撤回同意', icon: 'success' })
    }
    dialogMode.value = ''
  } catch (e) { dialogError.value = e.message || '操作未完成，请重试' }
  finally { operation.value = '' }
}
onShow(loadStatus)
</script>

<style scoped>
.privacy-page{max-width:1180px;margin:0 auto;min-height:100vh;padding:32px 24px 60px;box-sizing:border-box;background:var(--zy-bg,#f5f7f2);color:#233d34}
.privacy-hero{display:flex;justify-content:space-between;align-items:center;gap:24px;margin-bottom:28px}.hero-label{display:block;font-size:13px;color:#586e62;margin-bottom:12px}.hero-title{display:block;font-size:32px;font-weight:600;line-height:1.4}.hero-description{display:block;font-size:15px;line-height:1.8;color:#586e62;margin-top:12px}.privacy-hero image{width:112px;height:128px;flex-shrink:0}
.privacy-grid{display:grid;grid-template-columns:minmax(0,.85fr) minmax(0,1.15fr);gap:20px;align-items:start}.card{padding:28px;background:#fff;border:1px solid #dfe7dc;border-radius:20px;min-width:0}.section-label{display:block;font-size:12px;color:#586e62;margin-bottom:10px}.section-title{display:block;font-size:22px;font-weight:600;line-height:1.5}.permission-block{padding:24px 0;border-bottom:1px solid #e5ece1}.permission-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.permission-title{font-size:16px;font-weight:600}.body-text{display:block;font-size:14px;color:#586e62;line-height:1.9;margin-top:12px}.status-tag{font-size:12px;color:#6e5535;background:#f5edde;padding:5px 10px;border-radius:6px}.status-tag.enabled{color:#245f51;background:#e8efe4}.button-row{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px;margin-top:16px}
button{margin:0;font-size:14px;border-radius:10px;padding:10px 16px}.text-button{background:transparent;color:#245f51;padding-left:0;text-decoration:underline;text-underline-offset:3px}.secondary-button{background:#f5f7f2;color:#233d34;border:1px solid #dfe7dc}.primary-button{background:#2f7566;color:#fff}.contact-button{width:100%;background:#edf2e9;color:#245f51;border:1px solid #c8d8cb;margin-top:18px}.contact-button.enabled{background:#2f7566;color:#fff}.help-text{display:block;color:#6e5535;font-size:12px;margin-top:12px;line-height:1.8}
.authorization-note{margin-top:20px;padding:16px;background:#f5f7f2;border-radius:12px;color:#586e62;font-size:12px;line-height:1.8}.authorization-note text{display:block}.authorization-note text:first-child{color:#233d34;font-weight:600;margin-bottom:4px}.account-card{display:flex;align-items:center;justify-content:space-between;gap:28px;margin-top:24px}.account-card>view{max-width:800px}.danger-button{flex-shrink:0;background:#fff;color:#9c3f34;border:1px solid #d8b8b2}.danger-solid{background:#9c3f34;color:#fff}.footnote{display:block;font-size:12px;line-height:1.8;color:#586e62;text-align:center;margin-top:28px}.status-banner{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:16px;background:#e8efe4;border-radius:12px;font-size:14px;line-height:1.8;margin-bottom:20px}.error-banner{background:#fff2ed;color:#9c3f34}
.dialog-description{display:block;font-size:14px;line-height:1.9;color:#586e62}.confirmation-field{margin-top:20px}.field-label{display:block;font-size:14px;margin-bottom:10px}.confirmation-input{min-height:48px;box-sizing:border-box;padding:10px 14px;border:1px solid #b7c9bd;border-radius:10px;font-size:16px;width:100%;color:#233d34}.dialog-actions{display:flex;gap:12px;margin-top:24px}.dialog-actions button{flex:1}.dialog-error{display:block;color:#9c3f34;font-size:14px;line-height:1.8;margin-top:16px}
@media(max-width:900px){.privacy-grid{grid-template-columns:1fr}.account-card{align-items:flex-start;flex-direction:column}.account-card button{width:100%}}
@media(max-width:600px){.privacy-page{padding:20px 16px 44px}.privacy-hero{gap:12px;margin-bottom:20px}.hero-title{font-size:25px}.hero-description{font-size:13px}.privacy-hero image{width:72px;height:100px}.card{padding:22px 18px}.section-title{font-size:20px}.dialog-actions{flex-wrap:wrap}.dialog-actions button{min-width:100px}}
</style>
