<script>
export default {
	onLaunch: function () {
		console.log('织愈 App Launch')
	},
	onShow: function () {},
	onHide: function () {},
}
</script>

<style lang="scss">
/* 每个页面公共 css —— 冷调薄荷青主题（v3.0） */
page {
	background: var(--zy-bg, #ffffff);
	color: var(--zy-title, #33302a);
	font-size:var(--zy-text-body, 28rpx);
	line-height: 1.6;
}

/* AV 巡检修复：H5 占位文字默认色 #808080 仅 3.66:1，统一加深为正文令牌色（约 7:1）。
   仅影响未单独指定 placeholder-style 的输入框（内联 placeholder-style 优先）。 */
.uni-input-placeholder,
.uni-textarea-placeholder {
	color: var(--zy-text, #4a5f54);
}

/* ── 页面进入动效 ── */
.zy-page-enter {
	animation: fadeInUp var(--zy-duration-base, 250ms) var(--zy-ease-smooth, cubic-bezier(0.22, 1, 0.36, 1));
}
@keyframes fadeInUp {
	from { opacity: 0; transform: translateY(12rpx); }
	to { opacity: 1; transform: translateY(0); }
}

/* ── 骨架屏 shimmer ── */
.zy-skeleton {
	background: linear-gradient(90deg, #EFE8DA 25%, #F6F1E7 50%, #EFE8DA 75%);
	background-size: 200% 100%;
	animation: shimmer 1.5s ease-in-out infinite;
	border-radius: var(--zy-radius-sm, 12rpx);
}
@keyframes shimmer {
	0% { background-position: 200% 0; }
	100% { background-position: -200% 0; }
}
</style>

<style lang="scss">
/* wot-design-uni 青绿山水主题覆盖 */
@import '@/styles/theme.scss';
/* 织愈可复用动效类 */
@import '@/styles/animations.scss';
/* 暖纸青玉工具类（字阶/卡片/缝线/焦点环/纸纹） */
@import '@/styles/zy-toolkit.scss';
/* 通用信息页骨架（2026-11-11）：growth-* 在微信小程序端必须全局引入，
   页面级 <style src> 在小程序编译产物中会被丢弃（group/support 等页只剩 scoped 样式）。 */
@import '@/styles/growth.css';
</style>

<style>page,body,uni-page-body{font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;background-color:var(--zy-bg);color:var(--zy-title)}button:focus-visible{outline:3px solid var(--zy-brand-700);outline-offset:3px}/* #ifdef H5 */
/* wot-design-uni 图标字体 name 规则在 vite scoped 编译下被挂到单一 data-v 作用域，其他页面 content 缺失呈方框（2026-11-08 实测）；此处全局补一份无 scope 的码点表，仅覆盖项目用到的 23 个 name。mp 端组件样式隔离机制正常，不受影响。*/
.wd-icon-add::before { content: "\e65c"; }
.wd-icon-arrow-right::before { content: "\e655"; }
.wd-icon-arrow-up::before { content: "\e61d"; }
.wd-icon-chat::before { content: "\e647"; }
.wd-icon-check::before { content: "\e64e"; }
.wd-icon-check-circle-filled::before { content: "\e6fa"; }
.wd-icon-edit::before { content: "\e72f"; }
.wd-icon-fullsreen::before { content: "\e72c"; }
.wd-icon-usergroup::before { content: "\e73f"; }
.wd-icon-heart::before { content: "\e68f"; }
.wd-icon-image::before { content: "\e69a"; }
.wd-icon-lock-on::before { content: "\e690"; }
.wd-icon-star-filled::before { content: "\e67e"; }
.wd-icon-notification::before { content: "\e694"; }
.wd-icon-call::before { content: "\e69d"; }
.wd-icon-picture::before { content: "\e637"; }
.wd-icon-search::before { content: "\e63e"; }
.wd-icon-service::before { content: "\e682"; }
.wd-icon-setting::before { content: "\e621"; }
.wd-icon-tips::before { content: "\e683"; }
.wd-icon-star::before { content: "\e686"; }
.wd-icon-user::before { content: "\e73d"; }
.wd-icon-video::before { content: "\e62e"; }
/* #endif */@media(prefers-reduced-motion:reduce){.zy-page-enter,.zy-skeleton,.zy-slide-up,.zy-elastic{animation:none!important;transition:none!important}}/* #ifdef H5 */.uni-tabbar{display:none!important}uni-page-body{padding-bottom:calc(112rpx + env(safe-area-inset-bottom))}/* #endif */</style>
