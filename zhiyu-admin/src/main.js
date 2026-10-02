import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'

import './styles/index.scss'

const app = createApp(App)

// Pinia 必须先于 Router 安装：路由守卫里会用到 user store
app.use(createPinia())
app.use(router)

// Element Plus 按需自动导入（组件 + ElMessage/ElMessageBox 等 API）由
// vite.config.js 的 unplugin-auto-import / unplugin-vue-components 负责。
// locale 不再通过 app.use(ElementPlus) 全局设置，改为 ElConfigProvider 包裹。
app.component('ElConfigProvider', ElConfigProvider)

app.mount('#app')
