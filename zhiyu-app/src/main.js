import {
	createSSRApp
} from "vue";
import {
	createPinia
} from "pinia";
import App from "./App.vue";

// wot-design-uni：通过 EasyCom 按需引入组件，无需全局 css import
// 主题色通过 App.vue 引入 @/styles/theme.scss 覆盖 CSS 变量

export function createApp() {
	const app = createSSRApp(App);
	app.use(createPinia());
	return {
		app,
	};
}
