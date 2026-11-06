import { defineConfig, loadEnv } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

/**
 * 构建期硬校验：VITE_API_BASE_URL 缺失即让构建失败。
 *
 * 背景：src/utils/config.js 在该变量缺失时只能「运行时 throw」——产物照常构建成功，
 * 但 H5 白屏（被误读成后端超时）、小程序同样废，已三次在此耽误排查。
 * 这类「构建成功但产物必然不可用」必须在构建期就失败。
 *
 * 取舍：
 * - build（h5 / mp-weixin 均经过）：缺变量 → configResolved 抛错，构建直接失败，提示中文。
 * - dev（dev:h5 等）：只警告不失败——开发态 .env.development 已带 localhost 默认值，
 *   真缺了也有 config.js 的 DEV 兜底，强行失败会打断本地起服，故降级为醒目警告。
 *
 * 注意：这里读的是「构建环境」的变量（process.env + .env.[mode]），
 * 与 config.js 运行时读的 import.meta.env 同源，不会出现「构建期过、运行时缺」的错位。
 */
function requireApiBaseUrl() {
  return {
    name: 'zhiyu-require-api-base-url',
    // 在 uni() 之前跑，确保失败发生在产物写入之前
    enforce: 'pre',
    configResolved(config) {
      const env = loadEnv(config.mode, config.root, '')
      const raw = process.env.VITE_API_BASE_URL ?? env.VITE_API_BASE_URL
      const value = typeof raw === 'string' ? raw.trim() : ''
      const missing = !value || value === 'undefined'
      if (!missing) return

      const hint =
        '\n[织愈] 缺少必填环境变量 VITE_API_BASE_URL（后端接口根地址）。\n' +
        '  · 开发默认值见 zhiyu-app/.env.development（已含 http://localhost:8080）。\n' +
        '  · 生产/演示请在 CI 或部署平台注入，模板见 zhiyu-app/.env.production.example。\n' +
        '  · 微信小程序不能用 localhost，需填局域网 IP 或已备案 HTTPS 域名。\n'

      if (config.command === 'build') {
        // 构建期硬失败：让本次构建直接报错退出，不产出必然不可用的产物
        throw new Error(hint)
      } else {
        // 开发态降级为警告（不阻断本地起服）
        console.warn(hint)
      }
    },
  }
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    requireApiBaseUrl(),
    uni(),
  ],
})
