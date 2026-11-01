let current = null

export function stopSpeech() {
	if (current) {
		try {
			current.stop()
		} catch (e) {
			/* 播放器已释放 */
		}
		current = null
	}
}

/**
 * 播放服务端合成的 MP3。H5 用 Blob URL；小程序写临时文件后播放。
 * 调用方负责在页面卸载/重新录音前 stopSpeech()。
 */
export function playSpeech(base64, mimeType = 'audio/mpeg', handlers = {}) {
	stopSpeech()
	if (!base64) {
		handlers.onError?.(new Error('语音内容为空'))
		return
	}

	// #ifdef H5
	try {
		const binary = atob(base64)
		const bytes = new Uint8Array(binary.length)
		for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
		const url = URL.createObjectURL(new Blob([bytes], { type: mimeType }))
		const audio = new Audio(url)
		let stopped = false
		const item = {
			stop() {
				stopped = true
				audio.pause()
				audio.src = ''
				URL.revokeObjectURL(url)
			},
		}
		current = item
		audio.onended = () => {
			if (stopped) return
			if (current === item) current = null
			URL.revokeObjectURL(url)
			handlers.onEnd?.()
		}
		audio.onerror = () => {
			if (stopped) return
			if (current === item) current = null
			URL.revokeObjectURL(url)
			handlers.onError?.(new Error('语音播放失败'))
		}
		audio.play().catch(() => {
			if (stopped) return
			if (current === item) current = null
			URL.revokeObjectURL(url)
			handlers.onError?.(new Error('浏览器阻止了语音播放'))
		})
		return
	} catch (e) {
		handlers.onError?.(e)
		return
	}
	// #endif

	// #ifdef MP-WEIXIN
	try {
		const path = `${wx.env.USER_DATA_PATH}/zhiyu-tts-${Date.now()}.mp3`
		uni.getFileSystemManager().writeFile({
			filePath: path,
			data: base64,
			encoding: 'base64',
			success: () => {
				const audio = uni.createInnerAudioContext()
				const item = {
					stop() {
						audio.stop()
						audio.destroy()
						wx.getFileSystemManager().unlink({ filePath: path, fail: () => {} })
					},
				}
				current = item
				const cleanup = () => {
					if (current === item) current = null
					wx.getFileSystemManager().unlink({ filePath: path, fail: () => {} })
				}
				audio.onEnded(() => {
					cleanup()
					audio.destroy()
					handlers.onEnd?.()
				})
				audio.onError(() => {
					cleanup()
					audio.destroy()
					handlers.onError?.(new Error('语音播放失败'))
				})
				audio.src = path
				audio.play()
			},
			fail: () => handlers.onError?.(new Error('语音临时文件写入失败')),
		})
	} catch (e) {
		handlers.onError?.(e)
	}
	// #endif
}
