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
	color: var(--zy-title, #1F2937);
	font-size: 28rpx;
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
	background: linear-gradient(90deg, #E5E7EB 25%, #F3F4F6 50%, #E5E7EB 75%);
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
</style>

<style>page,body,uni-page-body{font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;background:var(--zy-bg);color:var(--zy-title)}button:focus-visible{outline:3px solid var(--zy-brand-700);outline-offset:3px}@media(prefers-reduced-motion:reduce){.zy-page-enter,.zy-skeleton,.zy-slide-up,.zy-elastic{animation:none!important;transition:none!important}}</style>
