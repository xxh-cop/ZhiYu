# 织愈 · UI Token 映射表

> 供阶段三页面改造时参考。将页面中常见的硬编码值替换为设计 Token（CSS 自定义属性）。
> Token 定义在 `zhiyu-app/src/styles/theme.scss` 的 `:root, page {}` 块中。

---

## 一、圆角映射

| 硬编码值 | Token | 说明 |
|---|---|---|
| `6rpx` | `var(--zy-radius-xs)` | 微圆角（标签内角） |
| `8rpx` / `4px` | `var(--zy-radius-sm)` | 小圆角（按钮、小卡片） |
| `12rpx` / `16rpx` / `16px` | `var(--zy-radius-md)` | 中圆角（卡片、气泡） |
| `24rpx` / `32rpx` | `var(--zy-radius-lg)` | 大圆角（模块容器） |
| `48rpx` | `var(--zy-radius-xl)` | 超大圆角（弹窗） |
| `999rpx` / `9999rpx` / `50%` | `var(--zy-radius-full)` | 全圆角（头像、胶囊） |

> **注意**：`50%` 用于圆形元素时保持 `50%`，不用 Token（语义不同）。

---

## 二、阴影映射

| 硬编码值 | Token | 说明 |
|---|---|---|
| `0 2rpx 4rpx rgba(45, 155, 143, 0.04)` | `var(--zy-shadow-xs)` | 微阴影（内嵌元素） |
| `0 2rpx 8rpx rgba(45, 155, 143, 0.06)` | `var(--zy-shadow-sm)` | 小阴影（卡片默认） |
| `0 4rpx 16rpx rgba(45, 155, 143, 0.08)` | `var(--zy-shadow-md)` | 中阴影（悬浮卡片） |
| `0 8rpx 24rpx rgba(45, 155, 143, 0.10)` | `var(--zy-shadow-lg)` | 大阴影（弹窗） |
| `0 12rpx 32rpx rgba(45, 155, 143, 0.12)` | `var(--zy-shadow-xl)` | 超大阴影（全屏遮罩） |
| `0 2rpx 8rpx rgba(0, 0, 0, 0.04)` | `var(--zy-shadow-sm)` | 中性灰阴影 → 用品牌色阴影 |
| `0 4rpx 16rpx rgba(0, 0, 0, 0.08)` | `var(--zy-shadow-md)` | 中性灰阴影 → 用品牌色阴影 |
| `0 4rpx 12rpx rgba(45, 155, 143, 0.2)` | `var(--zy-shadow-sm)` | 自定义强阴影 → 降级到小阴影 |

> **注意**：`drop-shadow()` 滤镜不能用 `var(--zy-shadow-*)`（语法不同），仅替换颜色部分为 `var(--zy-primary)`。

---

## 三、动效时长映射

| 硬编码值 | Token | 说明 |
|---|---|---|
| `100ms` / `0.1s` | `var(--zy-duration-instant)` | 瞬时反馈 |
| `150ms` / `0.15s` | `var(--zy-duration-fast)` | 快速过渡（按压） |
| `200ms` / `250ms` / `0.2s` / `0.25s` / `0.3s` | `var(--zy-duration-base)` | 标准过渡 |
| `400ms` / `0.4s` / `0.5s` | `var(--zy-duration-slow)` | 入场动画 |
| `600ms` / `0.6s` | `var(--zy-duration-slower)` | 复杂入场 |

> **注意**：`0.3s` 和 `0.25s` 都映射到 `--zy-duration-base`（250ms），差异在可接受范围。

---

## 四、缓动函数映射

| 硬编码值 | Token | 说明 |
|---|---|---|
| `linear` | `var(--zy-ease-linear)` | 线性 |
| `ease-out` | `var(--zy-ease-out)` | 减速出场 |
| `ease-in-out` | `var(--zy-ease-in-out)` | 标准缓动 |
| `cubic-bezier(0.22, 0.61, 0.36, 1)` | `var(--zy-ease-smooth)` | 丝滑缓动（近似） |
| `cubic-bezier(0.22, 1, 0.36, 1)` | `var(--zy-ease-smooth)` | 丝滑缓动（精确） |
| `cubic-bezier(0.34, 1.56, 0.64, 1)` | `var(--zy-ease-elastic)` | 弹性 |
| `cubic-bezier(0.68, -0.55, 0.27, 1.55)` | `var(--zy-ease-bounce)` | 弹跳 |

> **注意**：页面中常见的 `cubic-bezier(0.22, 0.61, 0.36, 1)` 与 Token `--zy-ease-smooth` 的 `cubic-bezier(0.22, 1, 0.36, 1)` 第二控制点不同（0.61 vs 1），视觉差异极小，可安全替换。

---

## 五、颜色映射

### 品牌色

| 硬编码值 | Token | 说明 |
|---|---|---|
| `#2D9B8F` / `#2d9b8f` | `var(--zy-primary)` | 主色 · 薄荷青 |
| `#5BC4A8` / `#5BCFBF` / `#5bc4a8` | `var(--zy-primary-light)` | 浅主色 |
| `#F0F9F7` | `var(--zy-bg-tint-mint)` | 薄荷青底色 |
| `#FFB547` | `var(--zy-accent)` | 强调色 · 暖橙 |
| `#FF6B6B` | `var(--zy-danger)` | 危险色 · 珊瑚红 |
| `#FFF8EE` | `var(--zy-bg-tint-warm)` | 暖橙底色 |

### 中性色

| 硬编码值 | Token | 说明 |
|---|---|---|
| `#F8F9FB` | `var(--zy-bg)` | 页面背景 |
| `#FFFFFF` / `#ffffff` / `#fff` | `var(--zy-card-bg)` | 卡片背景 |
| `#1F2937` / `#1f2937` | `var(--zy-title)` | 标题色 |
| `#4B5563` / `#4b5563` | `var(--zy-text)` / `var(--zy-content)` | 正文色 |
| `#9CA3AF` / `#9ca3af` | `var(--zy-muted)` / `var(--zy-aid)` | 辅助色 |
| `#E5E7EB` / `#e5e7eb` | `var(--zy-border)` | 边框色 |
| `#F3F4F6` / `#f3f4f6` | `var(--wot-color-border-light)` | 浅边框 |
| `#D1D5DB` / `#d1d5db` | `var(--wot-color-tip)` | 提示色 |
| `#6B7280` / `#6b7280` | `var(--wot-color-secondary)` | 次要色 |

---

## 六、间距映射（参考）

| 硬编码值 | Token | 说明 |
|---|---|---|
| `4rpx` | `var(--zy-space-2xs)` | 极小间距 |
| `8rpx` | `var(--zy-space-xs)` | 小间距 |
| `16rpx` | `var(--zy-space-sm)` | 中间距 |
| `24rpx` | `var(--zy-space-md)` | 标准间距 |
| `32rpx` | `var(--zy-space-lg)` | 大间距 |
| `48rpx` | `var(--zy-space-xl)` | 超大间距 |
| `64rpx` | `var(--zy-space-2xl)` | 最大间距 |

---

## 七、字体映射（参考）

| 硬编码值 | Token | 说明 |
|---|---|---|
| `20rpx` | `var(--zy-font-xs)` | 极小字 |
| `24rpx` | `var(--zy-font-sm)` | 小字 |
| `28rpx` | `var(--zy-font-base)` | 正文字 |
| `32rpx` | `var(--zy-font-md)` | 中字 |
| `36rpx` | `var(--zy-font-lg)` | 大字 |
| `44rpx` | `var(--zy-font-xl)` | 标题字 |
| `56rpx` | `var(--zy-font-2xl)` | 超大字 |

---

## 八、动效类映射

| 页面自定 | 全局类 | 说明 |
|---|---|---|
| `@keyframes staggerIn { opacity:0; translateY(24rpx→0) }` + `animation: staggerIn 0.5s ...` | `.zy-slide-up` + `.zy-stagger` | 错峰滑入 |
| `@keyframes fadeInUp { ... }` | `.zy-slide-up` | 淡入上滑 |
| `transition: transform 0.2s ease-out` + `:active { translateY(-2px) }` | `.zy-hover-lift` | 按压上浮 |
| 自定 `@keyframes` 渐隐 | `.zy-fade-in` | 淡入 |
| 自定 `@keyframes` 缩放 | `.zy-elastic` | 弹性弹入 |

---

## 九、改造检查清单

改造每个页面时，按此清单逐项检查：

- [ ] 所有 `border-radius` 硬编码 → `var(--zy-radius-*)`
- [ ] 所有 `box-shadow` 硬编码 → `var(--zy-shadow-*)`
- [ ] 所有 `transition` / `animation` 时长 → `var(--zy-duration-*)`
- [ ] 所有 `transition` / `animation` 缓动 → `var(--zy-ease-*)`
- [ ] 所有颜色硬编码 → `var(--zy-*)` 或 `var(--wot-*)`
- [ ] 自定 `@keyframes staggerIn` → 删除，改用 `.zy-slide-up` + `.zy-stagger`
- [ ] 自定 `:active` 按压 → 改用 `.zy-hover-lift`
- [ ] `padding` / `margin` 硬编码 → `var(--zy-space-*)`（可选，优先级低）
- [ ] `font-size` 硬编码 → `var(--zy-font-*)`（可选，优先级低）

---

*文档版本：阶段三 · 批次 1 · 2026-10-08*
