// 织结小精灵：双端共用同一套透明素材，旧 face 名保持兼容。
export const XIAOZHI_FRAMES = Object.freeze({
  standard: '/static/mascot/xiaozhi-standard.png',
  welcome: '/static/mascot/xiaozhi-welcome.png',
  listening: '/static/mascot/xiaozhi-listening.png',
  thinking: '/static/mascot/xiaozhi-thinking.png',
  crafting: '/static/mascot/xiaozhi-crafting.png',
  complete: '/static/mascot/xiaozhi-complete.png',
  rest: '/static/mascot/xiaozhi-rest.png',
  avatar: '/static/mascot/xiaozhi-avatar.png',
})

export const XIAOZHI_FADE_MS = 360

const FACE_MAP = Object.freeze({
  calm: 'standard', idle: 'standard',
  welcome: 'welcome',
  listen: 'listening', listening: 'listening', talking: 'listening',
  think: 'thinking', thinking: 'thinking',
  craft: 'crafting', crafting: 'crafting',
  happy: 'complete', complete: 'complete',
  care: 'listening', caring: 'listening',
  sleepy: 'rest', rest: 'rest',
})

export function xiaozhiFrame(face, petState = 'idle', size = 120) {
  if (size <= 48) return 'avatar'
  const key = face || petState
  return Object.prototype.hasOwnProperty.call(FACE_MAP, key) ? FACE_MAP[key] : 'standard'
}
