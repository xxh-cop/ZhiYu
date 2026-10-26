<template>
  <view class="export-card">
    <view class="export-head"><view><text class="eyebrow">02 / 保存副本</text><text class="heading">把自己的记录带走</text></view><text class="format">JSON 文件</text></view>
    <text class="description">选择记录类型，查看范围，再分批保存。每批最多 100 条；撤回隐私同意后仍可导出已有记录。</text>
    <view class="categories" role="group" aria-label="导出记录类型">
      <AccessibleButton v-for="item in categories" :key="item.key" :class="{ selected: category === item.key }"
        :disabled="loading || disabled" :aria-pressed="category === item.key" @click="select(item.key)">
        <text>{{ item.label }}</text><text v-if="progress[item.key].completed" class="done">已查看</text>
      </AccessibleButton>
    </view>
    <view class="scope" aria-live="polite"><text class="scope-title">{{ selected.label }} · 本次包含</text><text>{{ selected.description }}</text></view>
    <view class="progress" aria-live="polite"><text>{{ current.completed ? (current.count ? '这一类别已全部发起保存' : '这一类别暂无可导出记录') : '准备导出第 '+current.batch+' 批' }}</text><text>{{ current.count }} 条已发起保存</text></view>
    <text v-if="error" class="error" role="alert">{{ error }}</text>
    <AccessibleButton class="export-action" :loading="loading" :disabled="disabled" @click="download">
      {{ loading ? '正在准备文件…' : current.completed ? '重新检查这一类别' : current.batch > 1 ? '保存下一批记录' : '保存所选记录' }}
    </AccessibleButton>
    <text v-if="lastFile" class="last-file">最近发起保存：{{ lastFile }}</text>
    <text class="hint">请检查浏览器下载列表；小程序需在文件窗口中保存。文件含私人内容，请妥善保管。切换类别保留本页进度，离开后重置；导出期间若修改记录，请重新导出。</text>
    <AccessibleButton :disabled="disabled||loading" @click="openMedia">保存本人图片 / 视频原文件</AccessibleButton><text class="hint">当前不含媒体文件及地址、他人评论和审计日志，不是完整账号备份。</text>
  </view>
</template>

<script setup>
import { computed, reactive, ref, onBeforeUnmount } from 'vue'
import { http } from '../utils/request'
import AccessibleButton from './AccessibleButton.vue'
const props = defineProps({ disabled: Boolean })
const emit = defineEmits(['busy-change'])
const categories = [
  {key:'crafts',label:'创作过程',description:'本人的创作状态与专注时长。'},
  {key:'lessons',label:'课程进度',description:'本人完成的课节与记录时间。'},
  {key:'memberships',label:'参与小组',description:'本人的小组成员关系与角色，不含其他成员身份。'},
  {key:'group-messages',label:'组内发言',description:'仅本人发送的组内消息，不含其他成员消息。'},
  {key:'guidance-messages',label:'预约发言',description:'仅本人发送的预约消息，不含对方私人内容。'},
  {key:'comments',label:'我的评论',description:'本人发布的评论与审核状态。'},
  {key:'favorites',label:'点赞收藏',description:'本人的点赞、收藏与作品编号。'},
  {key:'media',label:'媒体清单',description:'本人受保护媒体的编号、大小和上传时间。原文件可在我的媒体中保存。'},
  { key: 'profile', label: '基本资料', description: '账号昵称、学校、简介、偏好和授权状态。不包含密码。' },
  { key: 'notes', label: '心情便签', description: '自己的便签正文、感受与记录时间。' },
  { key: 'sessions', label: '对话摘要', description: '自己的情绪会话状态、摘要与记录时间。' },
  { key: 'messages', label: '对话消息', description: '自己的会话内用户与助手消息，按记录编号分批保存。' },
  { key: 'records', label: '成长与反馈', description: '自己的心情、自评、创作感受、草稿和私密活动反馈。' },
  { key: 'works', label: '我的作品', description: '自己的作品标题、寄语、公开范围和审核状态，包含私密及待审核作品。不包含图片、视频或他人评论。' },
  { key: 'bookings', label: '活动预约', description: '自己的报名、取消、签到状态与活动基本信息。活动已删除时保留报名编号，不提供已删除的活动详情。' },
  { key: 'campus', label: '校园核验', description: '自己的学校、学院、姓名、学号和审核结果。这类文件包含身份信息，请特别注意保存位置。' },
]
function initial() { return { cursor: '0', batch: 1, count: 0, completed: false } }
const category = ref('profile'), progress = reactive(Object.fromEntries(categories.map(item => [item.key, initial()])))
const selected = computed(() => categories.find(item => item.key === category.value)), current = computed(() => progress[category.value])
const loading = ref(false), error = ref(''), lastFile = ref('')
function select(key) { if (!loading.value && !props.disabled) { category.value = key; error.value = '' } }
function openMedia(){uni.navigateTo({url:'/pages/privacy/media'})}
let active = true
onBeforeUnmount(() => { active = false })
async function saveFile(content, filename) {
  // #ifdef H5
  const url = URL.createObjectURL(new Blob([content], { type: 'application/json;charset=utf-8' }))
  const link = document.createElement('a'); link.href = url; link.download = filename
  document.body.appendChild(link); link.click(); link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 30000)
  return
  // #endif
  // #ifdef MP-WEIXIN
  if (!wx.shareFileMessage) throw new Error('当前微信版本不支持文件导出，请升级微信或使用网页版')
  const fs = wx.getFileSystemManager(), path = wx.env.USER_DATA_PATH + '/' + filename
  await new Promise((resolve, reject) => fs.writeFile({ filePath: path, data: content, encoding: 'utf8', success: resolve, fail: reject }))
  try { await new Promise((resolve, reject) => wx.shareFileMessage({ filePath: path, fileName: filename, success: resolve, fail: reject })) }
  finally { fs.unlink({ filePath: path, fail: () => {} }) }
  return
  // #endif
  // #ifndef H5
  // #ifndef MP-WEIXIN
  throw new Error('请使用网页版导出记录')
  // #endif
  // #endif
}
async function download() {
  if (loading.value || props.disabled) return
  const key = category.value
  if (current.value.completed) progress[key] = initial()
  const state = progress[key]
  loading.value = true; error.value = ''; emit('busy-change', true)
  try {
    const data = await http.get('/api/user/data-export', { data: { category: key, after: state.cursor } })
    if (!active) return
    if (data.rows.length) {
      const filename = 'zhiyu-' + key + '-' + Date.now() + '-part-' + state.batch + '.json'
      await saveFile(JSON.stringify(data, null, 2), filename)
      lastFile.value = filename
    }
    state.count += data.rows.length
    state.completed = !data.hasMore
    if (data.hasMore) { state.cursor = data.nextCursor; state.batch++ }
  } catch (e) { error.value = e.message || '导出未完成或已取消，可重试当前批次' }
  finally { loading.value = false; emit('busy-change', false) }
}
</script>

<style scoped>
.export-card{padding:28px;background:#fff;border:1px solid #dfe7dc;border-radius:20px;color:#233d34;min-width:0}
.export-head{display:flex;align-items:center;justify-content:space-between;gap:16px}.eyebrow,.heading,.description,.hint{display:block}.eyebrow{font-size:12px;color:#586e62;margin-bottom:10px}.heading{font-size:22px;font-weight:600;line-height:1.5}.format{font-size:12px;background:#e8efe4;padding:8px 12px;border-radius:8px;white-space:nowrap}
.description,.hint{font-size:14px;line-height:1.8;color:#586e62;margin-top:16px}.categories{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin:24px 0}.categories button{margin:0;padding:12px 8px;font-size:14px;flex-wrap:wrap;background:#f5f7f2;color:#586e62;border:1px solid transparent;border-radius:10px}.categories button.selected{background:#e8efe4;color:#245f51;border-color:#2f7566}.done{font-size:10px;color:#586e62}.scope{padding:18px;background:#f5f7f2;border-radius:12px;line-height:1.8;font-size:14px;color:#586e62}.scope-title{display:block;color:#233d34;font-weight:600;margin-bottom:4px}.progress{display:flex;flex-wrap:wrap;justify-content:space-between;gap:10px;font-size:13px;color:#586e62;margin:22px 0}.export-action{background:#2f7566;color:#fff;font-size:15px;border-radius:10px;padding:14px;width:100%}.error{display:block;color:#9c3f34;font-size:14px;margin:16px 0;line-height:1.7}.hint{font-size:12px}.last-file{display:block;overflow-wrap:anywhere;font-size:12px;line-height:1.7;color:#586e62;margin-top:16px}
@media(max-width:600px){.export-card{padding:22px 18px}.categories{grid-template-columns:repeat(2,minmax(0,1fr))}.heading{font-size:20px}.format{display:none}}
</style>
