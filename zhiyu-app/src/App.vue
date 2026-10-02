<script>
export default {
	onLaunch: function () {
		console.log('织愈 App Launch')
	},
	onShow: function () {},
	onHide: function () {},
}
</script>

<style lang="less">
/* Leaf Island UI 全局样式（CSS 变量 + reset） */
@import '@/uni_modules/leaf-island/index.less';

/* 每个页面公共 css —— 暖色调疗愈主题 + 呼吸渐变背景（覆盖 leaf-island 默认 page 样式） */
page {
	background: radial-gradient(circle at 30% 20%, #f5ede1 0%, #f7f4ef 50%, #f0ebe1 100%);
	background-size: 200% 200%;
	background-position: 0% 0%;
	animation: breathingBg 20s ease-in-out infinite;
	color: #3D3A34;
	font-size: 28rpx;
	line-height: 1.6;
}

@keyframes breathingBg {
	0%, 100% { background-position: 0% 0%; }
	50% { background-position: 100% 100%; }
}

/* ── 全局治愈系卡片样式 ── */
.zy-card {
	border-radius: 16px;
	background-color: rgba(255, 255, 255, 0.85);
	border: 1px solid rgba(139, 111, 71, 0.08);
	box-shadow: 0 4px 16px rgba(139, 111, 71, 0.06);
}

/* ── 全局按钮交互 ── */
.zy-btn-primary {
	background-color: #8b6f47;
	border-radius: 12px;
	transition: all 0.15s ease;
}
.zy-btn-primary:active {
	transform: scale(0.97);
	box-shadow: 0 2px 8px rgba(139, 111, 71, 0.12);
}

.zy-btn-outline {
	border: 1px solid rgba(139, 111, 71, 0.2);
	border-radius: 12px;
	background-color: transparent;
	transition: all 0.15s ease;
}
.zy-btn-outline:active {
	background-color: rgba(139, 111, 71, 0.05);
}

/* ── 页面进入动效 ── */
.zy-page-enter {
	animation: fadeInUp 0.3s ease-out;
}
@keyframes fadeInUp {
	from { opacity: 0; transform: translateY(12rpx); }
	to { opacity: 1; transform: translateY(0); }
}

/* ── 骨架屏 shimmer ── */
.zy-skeleton {
	background: linear-gradient(90deg, #f0ebe1 25%, #e5ddd0 50%, #f0ebe1 75%);
	background-size: 200% 100%;
	animation: shimmer 1.5s ease-in-out infinite;
	border-radius: 8px;
}
@keyframes shimmer {
	0% { background-position: 200% 0; }
	100% { background-position: -200% 0; }
}

/* ── 可点击卡片按压反馈 ── */
.zy-clickable {
	transition: transform 0.15s ease;
}
.zy-clickable:active {
	transform: scale(0.98);
}
</style>
