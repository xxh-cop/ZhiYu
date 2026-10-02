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
 */
function startSSEMP({ url, body, token, onMessage, onDone, onError }) {
	let aborted = false
	let buffer = ''

	const requestTask = wx.request({
		url: url,
		method: 'POST',
		enableChunked: true,
		header: {
			'Content-Type': 'application/json',
			Accept: 'text/event-stream',
			Authorization: token ? 'Bearer ' + token : '',
		},
		data: JSON.stringify(body),
		success(res) {
			// 请求完成（流结束），处理 buffer 剩余
			if (buffer.trim()) {
				parseSSEChunk(buffer, onMessage, onDone, onError, aborted)
			}
		},
		fail(err) {
			if (!aborted) {
				onError({ code: -1, message: (err && err.errMsg) || '网络异常' })
			}
		},
	})

	// 监听分块数据
	if (requestTask && requestTask.onChunkReceived) {
		requestTask.onChunkReceived((res) => {
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
