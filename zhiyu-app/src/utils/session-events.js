/**
 * session-events —— 创作会话行为事件上报器（BS，2026-11-05）
 *
 * 契约：POST /api/craft/session/{id}/events {events:[1-100]} → {accepted, deduped, failed}
 * 幂等键 (session_id, client_seq)；occurredAt 允许 5 分钟时钟偏移。
 *
 * 设计：
 *  - 内存队列 + 批量提交（满 5 条或每 20s 定时 flush；切片 ≤100）；
 *  - clientSeq 每会话单调递增，按会话持久化到本地存储（重进续号，不与旧 seq 冲突被 deduped 误杀）；
 *  - 失败静默重试：同批最多重试 2 次（指数退避 2s/8s），仍失败丢弃——数据可丢，流程不能断；
 *  - 铁律：绝不阻塞/打断创作流程——不弹 toast、不 throw、所有路径 catch 到底；
 *  - 只绑定进行中会话；只读回看（BR）不绑定，天然零事件。
 */
import { http } from './request'

const FLUSH_SIZE = 5
const FLUSH_INTERVAL = 20000
const MAX_RETRY = 2
const SEQ_KEY_PREFIX = 'bs-seq-'

const state = {
	sessionId: null,
	queue: [],
	seq: 0,
	timer: null,
	flushing: null,
	bound: false,
}

function readSeq(sessionId) {
	try { return Number(uni.getStorageSync(SEQ_KEY_PREFIX + sessionId)) || 0 } catch (e) { return 0 }
}
function writeSeq() {
	try { uni.setStorageSync(SEQ_KEY_PREFIX + state.sessionId, state.seq) } catch (e) { /* 存储不可用时内存 seq 仍单调 */ }
}
/** 会话结束（完成/放弃）后清除序号键，避免长期膨胀 */
function clearSeq(sessionId) {
	try { uni.removeStorageSync(SEQ_KEY_PREFIX + sessionId) } catch (e) { /* noop */ }
}

/** 绑定会话并开始周期 flush；重复绑定同会话幂等 */
export function bindSessionEvents(sessionId) {
	if (!sessionId) return
	if (state.bound && state.sessionId === String(sessionId)) return
	unbindSessionEvents()
	state.sessionId = String(sessionId)
	state.seq = readSeq(state.sessionId)
	state.queue = []
	state.bound = true
	state.timer = setInterval(() => { flushSessionEvents() }, FLUSH_INTERVAL)
	if (state.timer && typeof state.timer.unref === 'function') state.timer.unref()
}

/** 解绑：停定时器并尽力 flush（不 await，调用方需要时用 flushSessionEvents 显式等待） */
export function unbindSessionEvents() {
	if (state.timer) { clearInterval(state.timer); state.timer = null }
	if (state.bound) flushSessionEvents()
	state.bound = false
	state.sessionId = null
	state.queue = []
	state.seq = 0
}

/** 会话终结（完成/放弃）：尽力 flush 并清理序号键 */
export async function finishSessionEvents() {
	const sid = state.sessionId
	try { await flushSessionEvents() } catch (e) { /* 静默 */ }
	if (state.timer) { clearInterval(state.timer); state.timer = null }
	state.bound = false
	state.sessionId = null
	state.queue = []
	state.seq = 0
	if (sid) clearSeq(sid)
}

/**
 * 入队一条事件。未绑定会话 / 事件类型非法时静默丢弃。
 * @param {string} eventType 白名单类型（step_enter/step_leave/audio_play/visibility_change/app_lifecycle/breathing_responded）
 * @param {{ stepNo?: number, context?: Record<string, number|boolean|string> }} [opts]
 */
export function trackSessionEvent(eventType, opts = {}) {
	if (!state.bound || !state.sessionId || !eventType) return
	state.seq += 1
	writeSeq()
	state.queue.push({
		eventType,
		stepNo: typeof opts.stepNo === 'number' ? opts.stepNo : null,
		occurredAt: new Date().toISOString(),
		clientSeq: state.seq,
		context: opts.context || null,
	})
	if (state.queue.length >= FLUSH_SIZE) flushSessionEvents()
}

/** 批量提交；任何失败静默（重试上限后丢弃）。永远 resolve，绝不 reject。 */
export async function flushSessionEvents() {
	if (!state.sessionId || !state.queue.length) return
	if (state.flushing) return state.flushing
	const sid = state.sessionId
	const batch = state.queue.splice(0, 100)
	state.flushing = (async () => {
		for (let attempt = 0; attempt <= MAX_RETRY; attempt++) {
			try {
				await http.post('/api/craft/session/' + sid + '/events', { events: batch })
				return // accepted/deduped/failed 均为正常受理（deduped 幂等、failed 为服务端丢弃，均不重试）
			} catch (e) {
				// 1001（事件非法）/1003/1004 重试无意义，直接丢弃；其余（网络/5xx）退避重试
				if (e && typeof e.code === 'number' && [1001, 1003, 1004, 1002].includes(e.code)) return
				if (attempt < MAX_RETRY) await new Promise((r) => setTimeout(r, attempt === 0 ? 2000 : 8000))
			}
		}
	})()
	try { await state.flushing } catch (e) { /* 静默 */ } finally { state.flushing = null }
}
