/**
 * 自定义 SSE 封装（T1.9a）。
 *
 * 不依赖第三方插件，用条件编译分别实现：
 * - H5：fetch + ReadableStream + TextDecoder
 * - MP-WEIXIN：wx.request + enableChunked + onChunkReceived
 *
 * SSE 协议解析：
 * - 按 \n\n 分割事件块
 * - 每个块解析 event: 和 data: 行
 * - 默认事件（无 event 名）→ 视为 message，data 是纯文本 token
 * - event: done → data 是 JSON { sessionId: "..." }
 * - event: error → data 是 JSON { code, message }
 *
 * abort() 时静默关闭，不触发 onError。
 */

// #ifdef H5
/**
 * H5 端实现：fetch + ReadableStream。
 */
function startSSEH5({ url, body, token, onMessage, onDone, onError }) {
	const controller = new AbortController()
	let aborted = false

	const headers = {
		'Content-Type': 'application/json',
		Accept: 'text/event-stream',
	}
	if (token) {
		headers.Authorization = 'Bearer ' + token
	}

	fetch(url, {
		method: 'POST',
		headers,
		body: JSON.stringify(body),
		signal: controller.signal,
	})
		.then((response) => {
			if (!response.ok) {
				if (response.status === 401) {
					if (!aborted) {
						onError({ code: 1002, message: '未登录或登录已过期' })
					}
					return
				}
				if (!aborted) {
					onError({ code: response.status, message: 'HTTP ' + response.status })
				}
				return
			}
			const reader = response.body.getReader()
			const decoder = new TextDecoder('utf-8')
			let buffer = ''

			function pump() {
				reader
					.read()
					.then(({ done, value }) => {
						if (done) {
							// 流结束，处理 buffer 剩余
							if (buffer.trim()) {
								parseSSEChunk(buffer, onMessage, onDone, onError, aborted)
							}
							return
						}
						buffer += decoder.decode(value, { stream: true })
						// 按 \n\n 分割事件块
						const parts = buffer.split('\n\n')
						buffer = parts.pop() // 最后一段可能不完整，保留
						for (const part of parts) {
							if (part.trim()) {
								parseSSEChunk(part, onMessage, onDone, onError, aborted)
							}
						}
						pump()
					})
					.catch((err) => {
						if (!aborted) {
							onError({ code: -1, message: (err && err.message) || '网络异常' })
						}
					})
			}
			pump()
		})
		.catch((err) => {
			if (!aborted) {
				onError({ code: -1, message: (err && err.message) || '网络异常' })
			}
		})

	return {
		abort() {
			aborted = true
			controller.abort()
		},
	}
}
// #endif

// #ifdef MP-WEIXIN
/**
 * 微信小程序端实现：wx.request + enableChunked + onChunkReceived。
 *
 * 兼容策略：
 * 1. 优先使用 enableChunked + onChunkReceived 实现真正的流式接收（基础库 ≥ 2.20.1）
 * 2. 如果 onChunkReceived 不可用，退回 enableChunked: false 一次性接收完整 SSE 再逐块解析
 * 3. 如果流式模式 success 回调里 onChunkReceived 未触发，也在 success 中处理完整响应
 */
function startSSEMP({ url, body, token, onMessage, onDone, onError }) {
	let aborted = false
	let buffer = ''
	let chunkReceived = false

	// 检测 onChunkReceived 是否可用（基础库 ≥ 2.20.1）
	// 通过创建一个临时 RequestTask 来检测不可行，改用 canIUse
	const chunkSupported = wx.canIUse && wx.canIUse('RequestTask.onChunkReceived')

	const headers = {
		'Content-Type': 'application/json',
		Accept: 'text/event-stream',
		Authorization: token ? 'Bearer ' + token : '',
	}

	/** 处理完整 SSE 文本（fallback 模式或 success 剩余） */
	function processFullText(fullText) {
		if (!fullText || !fullText.trim()) return
		const parts = fullText.split('\n\n')
		for (const part of parts) {
			if (part.trim()) {
				parseSSEChunk(part, onMessage, onDone, onError, aborted)
			}
		}
	}

	const requestTask = wx.request({
		url: url,
		method: 'POST',
		enableChunked: chunkSupported,
		header: headers,
		data: JSON.stringify(body),
		success(res) {
			if (aborted) return

			// 流式模式：onChunkReceived 已处理了大部分数据，只处理 buffer 剩余
			if (chunkSupported && chunkReceived) {
				if (buffer.trim()) {
					parseSSEChunk(buffer, onMessage, onDone, onError, aborted)
				}
				return
			}

			// Fallback 模式：处理完整响应
			// res.data 可能是字符串、ArrayBuffer 或被自动解析的对象
			let fullText = ''
			if (res.data != null) {
				if (typeof res.data === 'string') {
					fullText = res.data
				} else if (res.data instanceof ArrayBuffer) {
					fullText = arrayBufferToUtf8(res.data)
				} else {
					// 微信可能根据 Content-Type 自动解析，SSE 的 text/event-stream
					// 可能被当作纯文本处理，也可能被当作其他格式
					fullText = String(res.data)
				}
			}

			// 如果 enableChunked 模式下 success 拿不到 data，
			// 但 buffer 有内容（onChunkReceived 触发过），处理 buffer
			if (!fullText && buffer.trim()) {
				parseSSEChunk(buffer, onMessage, onDone, onError, aborted)
				return
			}

			processFullText(fullText)
		},
		fail(err) {
			if (!aborted) {
				onError({ code: -1, message: (err && err.errMsg) || '网络异常' })
			}
		},
	})

	// 监听分块数据（仅流式模式）
	if (chunkSupported && requestTask && requestTask.onChunkReceived) {
		requestTask.onChunkReceived((res) => {
			chunkReceived = true
			// res.data 是 ArrayBuffer
			try {
				const text = arrayBufferToUtf8(res.data)
				buffer += text
				// 按 \n\n 分割事件块
				const parts = buffer.split('\n\n')
				buffer = parts.pop()
				for (const part of parts) {
					if (part.trim()) {
						parseSSEChunk(part, onMessage, onDone, onError, aborted)
					}
				}
			} catch (e) {
				// 解码失败，忽略
			}
		})
	}

	return {
		abort() {
			aborted = true
			if (requestTask) {
				requestTask.abort()
			}
		},
	}
}

/** ArrayBuffer → UTF-8 字符串（小程序端无 TextDecoder） */
function arrayBufferToUtf8(buf) {
	const bytes = new Uint8Array(buf)
	let str = ''
	let i = 0
	while (i < bytes.length) {
		const c = bytes[i]
		if (c < 0x80) {
			str += String.fromCharCode(c)
			i++
		} else if (c < 0xe0) {
			str += String.fromCharCode(((c & 0x1f) << 6) | (bytes[i + 1] & 0x3f))
			i += 2
		} else if (c < 0xf0) {
			str += String.fromCharCode(
				((c & 0x0f) << 12) | ((bytes[i + 1] & 0x3f) << 6) | (bytes[i + 2] & 0x3f)
			)
			i += 3
		} else {
			const cp =
				((c & 0x07) << 18) |
				((bytes[i + 1] & 0x3f) << 12) |
				((bytes[i + 2] & 0x3f) << 6) |
				(bytes[i + 3] & 0x3f)
			const s = cp - 0x10000
			str += String.fromCharCode(0xd800 + (s >> 10), 0xdc00 + (s & 0x3ff))
			i += 4
		}
	}
	return str
}
// #endif

/**
 * 解析单个 SSE 事件块。
 *
 * 一个块形如：
 *   event: done\n
 *   data: {"sessionId":"123"}
 *
 * 或默认事件：
 *   data: 你好
 */
function parseSSEChunk(chunk, onMessage, onDone, onError, aborted) {
	if (aborted) return

	let event = ''
	let dataLines = []

	const lines = chunk.split('\n')
	for (const line of lines) {
		if (line.startsWith('event:')) {
			event = line.slice(6).trim()
		} else if (line.startsWith('data:')) {
			dataLines.push(line.slice(5).trim())
		}
	}

	const data = dataLines.join('\n')

	if (event === 'done') {
		try {
			const parsed = JSON.parse(data)
			onDone(parsed)
		} catch (e) {
			onDone({ sessionId: null })
		}
	} else if (event === 'error') {
		try {
			const parsed = JSON.parse(data)
			onError(parsed)
		} catch (e) {
			onError({ code: 5000, message: '服务暂时不可用' })
		}
	} else {
		// 默认事件 → message，data 是纯文本 token
		if (data) {
			onMessage(data)
		}
	}
}

/**
 * 发起 SSE 请求。
 *
 * @param {object} options
 * @param {string} options.url       完整 URL（含 baseURL）
 * @param {object} options.body      请求体（会被 JSON.stringify）
 * @param {string} options.token     JWT token（不含 "Bearer " 前缀）
 * @param {function} options.onMessage  收到 token 时回调，参数为文本
 * @param {function} options.onDone     收到 done 事件回调，参数为 { sessionId }
 * @param {function} options.onError    收到 error 事件或网络异常回调
 * @returns {{ abort: function }}  调用 abort() 静默关闭
 */
export function startSSE(options) {
	// #ifdef H5
	return startSSEH5(options)
	// #endif

	// #ifdef MP-WEIXIN
	return startSSEMP(options)
	// #endif
}
