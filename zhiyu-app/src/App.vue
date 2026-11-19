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
	color: #8B9994 !important;
	font-size: 24rpx !important;
	font-weight: 400 !important;
	letter-spacing: 0.4rpx;
}

input::placeholder,
textarea::placeholder {
	color: #8B9994 !important;
	font-size: 24rpx !important;
	font-weight: 400 !important;
	letter-spacing: 0.4rpx;
}

/* ── 页面进入动效 ── */
.zy-page-enter {
	animation: fadeInUp var(--zy-duration-base, 250ms) var(--zy-ease-smooth, cubic-bezier(0.22, 1, 0.36, 1));
}
@keyframes fadeInUp {
	from { opacity: 0; transform: translateY(12rpx); }
	to { opacity: 1; transform: translateY(0); }
}

/* #ifdef H5 */
/* 页面级入场编排：未显式声明 zy-page-enter 的页面也获得同一节奏，避免硬切。
   已声明进入动效的页面不叠加，防止位移翻倍。 */
@media (prefers-reduced-motion: no-preference) {
	uni-page-body:not(:has(.zy-page-enter)) { animation: zyPageIn 260ms cubic-bezier(0.22, 1, 0.36, 1); }
	@keyframes zyPageIn {
		from { opacity: .94; transform: translateY(10rpx); }
		to { opacity: 1; transform: translateY(0); }
	}
}
/* #endif */

/* ── 骨架屏 shimmer ── */
.zy-skeleton {
	background: linear-gradient(90deg, #e5eae6 25%, #f3f6f3 50%, #e5eae6 75%);
	background-size: 200% 100%;
	animation: shimmer 1.5s ease-in-out infinite;
	border-radius: 0;
}
@keyframes shimmer {
	0% { background-position: 200% 0; }
	100% { background-position: -200% 0; }
}

/* ── 玄墨主题组件例外（全局，特异性高于组件 scoped 规则） ──
   令牌已覆盖面级；这里只处理「硬编码白字/浅底假设」的组件细节。 */
/* 主按钮：深底品牌填充加深后仍配白字（check:contrast 深色组 5.30:1）；按压态用更深填充保持白字可读 */
.zy-theme-dark .zy-btn.zy-btn--primary { color: #ffffff; }
.zy-theme-dark .zy-btn.zy-btn--primary:active { background: #25685f; }
.zy-theme-dark .zy-btn.zy-btn--care { background: #a64842; color: #ffffff; }
.zy-theme-dark .zy-btn.zy-btn--care:active { background: #8f3a35; }
.zy-theme-dark .zy-btn.zy-btn--danger { border-color: rgba(212, 115, 106, 0.5); }
.zy-theme-dark.zy-theme-dark .zy-tabbar-item.active { color: #8fc7b8; }
.zy-theme-dark .zy-skeleton { background: linear-gradient(90deg, #142019 25%, #1b2b24 50%, #142019 75%); background-size: 200% 100%; }
/* 输入底线在深底提亮（growth 遗留控件与 Zy 组件共用令牌） */
.zy-theme-dark .growth-input, .zy-theme-dark .growth-textarea { border-bottom-color: #5f7a70; }
.zy-theme-dark .growth-input:focus, .zy-theme-dark .growth-textarea:focus { border-bottom-color: #8fc7b8; }
/* 等级徽章：浅底用窑青实底白字；深底改深青底浅青字，避免白字压浅绿
   （双写主题类提高特异性，压过页面 scoped 同级规则） */
.zy-theme-dark.zy-theme-dark .level-badge { background: #1b332d; border: 1rpx solid rgba(143, 199, 184, 0.4); }
.zy-theme-dark.zy-theme-dark .level-text { color: #8fc7b8; }
/* 朱砂小标签（推荐等）：深底提亮后白字对比不足，改墨字 */
.zy-theme-dark.zy-theme-dark .short-recommend { color: #26120f; }
/* growth 骨架屏深底渐变 */
.zy-theme-dark .growth-skeleton { background: linear-gradient(90deg, #142019, #1b2b24, #142019); background-size: 200% 100%; }
/* 倾听室/音乐本就是深色原生页：玄墨下与全站一致，无需额外处理 */
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
