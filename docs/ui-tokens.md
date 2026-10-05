# 织愈 · 设计 Token 对照表

> 建立于 UI 冲刺阶段一。两端共用间距/圆角/动效，色板可差异化。
> 参考项目：Art Design Pro（圆角/阴影/动效 Token 体系）、Leaf Island UI（圆润形态/低饱和暖色/弹入缓动）、Spark UI（Aurora Text / Meteors / Bento Grid 动效思路）。

## 1. 色板

| Token | 学生端值 | 管理端值 | 用途 |
|---|---|---|---|
| `--zy-primary` / `--zy-admin-primary` | `#2D9B8F` | `#2D9B8F` | 品牌主色：CTA 按钮、图标选中态、Logo |
| `--zy-primary-light` / `--zy-admin-primary-light` | `#5BC4A8` | `#5BC4A8` | 渐变辅助：hover 态、主色到浅色过渡 |
| `--zy-bg` / `--zy-admin-bg` | `#F8F9FB` | `#F8F9FB` | 页面背景 |
| `--zy-card-bg` / `--zy-admin-card-bg` | `#FFFFFF` | `#FFFFFF` | 卡片底色 |
| `--zy-title` / `--zy-admin-title` | `#1F2937` | `#1F2937` | 标题文字（替代纯黑） |
| `--zy-text` / `--zy-admin-text` | `#4B5563` | `#4B5563` | 正文文字 |
| `--zy-muted` / `--zy-admin-muted` | `#9CA3AF` | `#9CA3AF` | 辅助文字、占位符 |
| `--zy-border` / `--zy-admin-border` | `#E5E7EB` | `#E5E7EB` | 边框、分割线 |
| `--zy-accent` / `--zy-admin-accent` | `#FFB547` | `#FFB547` | 徽章、数字高亮、暖橙点缀 |
| `--zy-danger` / `--zy-admin-danger` | `#FF6B6B` | `#FF6B6B` | 删除、危险操作、点赞 |
| `--zy-admin-success` | — | `#10B981` | 成功状态（管理端数据看板专用） |
| `--zy-admin-warning` | — | `#F59E0B` | 警告状态（管理端审核流专用） |
| `--zy-admin-info` | — | `#6B7280` | 信息状态（管理端中性提示） |

## 2. 圆角

| Token | 学生端值 (rpx) | 管理端值 (px) | 用途 |
|---|---|---|---|
| `--zy-radius-xs` | `6rpx` | `2px` | 极小元素：标签内描边、图标角 |
| `--zy-radius-sm` | `12rpx` | `4px` | 小元素：按钮、输入框（参考 Art Design Pro `--radius-sm`） |
| `--zy-radius-md` | `24rpx` | `8px` | 中等元素：卡片、弹窗（参考 Art Design Pro `--radius-md`） |
| `--zy-radius-lg` | `32rpx` | `12px` | 大卡片：数据卡片、推荐卡（参考 Art Design Pro `--radius-lg`） |
| `--zy-radius-xl` | `48rpx` | `16px` | 超大圆角：全屏弹窗底板（参考 Art Design Pro `--radius-xl`） |
| `--zy-radius-full` | `9999rpx` | `9999px` | 胶囊形：头像、药丸标签 |

> 设计思路：学生端用 rpx 适配多端缩放，管理端用 px 精确控制。圆角档位参考 Art Design Pro 的 4/8/12/16 体系，学生端按 2x 换算为 rpx。

## 3. 阴影

| Token | 学生端值 (rpx) | 管理端值 (px) | 用途 |
|---|---|---|---|
| `--zy-shadow-xs` | `0 2rpx 4rpx rgba(45,155,143,0.04)` | `0 1px 2px rgba(45,155,143,0.04)` | 极轻：静态卡片底纹 |
| `--zy-shadow-sm` | `0 2rpx 8rpx rgba(45,155,143,0.06)` | `0 2px 8px rgba(45,155,143,0.06)` | 轻：默认卡片阴影（参考 Art Design Pro `--shadow-sm`） |
| `--zy-shadow-md` | `0 4rpx 16rpx rgba(45,155,143,0.08)` | `0 4px 16px rgba(45,155,143,0.08)` | 中：悬浮卡片、hover 态（参考 Art Design Pro `--shadow-md`） |
| `--zy-shadow-lg` | `0 8rpx 24rpx rgba(45,155,143,0.10)` | `0 8px 24px rgba(45,155,143,0.10)` | 重：弹窗、抽屉 |
| `--zy-shadow-xl` | `0 12rpx 32rpx rgba(45,155,143,0.12)` | `0 12px 32px rgba(45,155,143,0.12)` | 超重：全屏遮罩上层 |

> 设计思路：阴影统一使用主色（薄荷青 `#2D9B8F` = `rgb(45,155,143)`）的透明度叠加，保持"纸上浮起一层"的轻柔感，不使用纯黑阴影。参考 Leaf Island UI 的极轻阴影策略。

## 4. 间距

| Token | 学生端值 (rpx) | 管理端值 (px) | 用途 |
|---|---|---|---|
| `--zy-space-2xs` | `4rpx` | `2px` | 极小间距：图标内描边 |
| `--zy-space-xs` | `8rpx` | `4px` | 小间距：图标与文字间 |
| `--zy-space-sm` | `16rpx` | `8px` | 小间距：卡片内文字与数值 |
| `--zy-space-md` | `24rpx` | `12px` | 中间距：表单项内部、按钮组 |
| `--zy-space-lg` | `32rpx` | `16px` | 大间距：卡片内边距、表单项间 |
| `--zy-space-xl` | `48rpx` | `24px` | 超大间距：卡片之间、区块之间 |
| `--zy-space-2xl` | `64rpx` | `32px` | 最大间距：页面主内容区外边距 |
| `--zy-space-3xl` | — | `48px` | 管理端额外大间距：看板行间距 |

> 设计思路：基准 4px（管理端）/ 8rpx（学生端 750 设计稿），按 2x 递进。参考 Art Design Pro 的间距梯度体系。

## 5. 动效

### 5.1 时长

| Token | 值 | 用途 |
|---|---|---|
| `--zy-duration-instant` | `100ms` | 瞬时反馈：触摸反馈、波纹 |
| `--zy-duration-fast` | `150ms` | 微交互：眨眼、按钮缩放、气泡入场（参考 Art Design Pro `--transition-fast`） |
| `--zy-duration-base` | `250ms` | 元素显隐：弹窗、Toast、卡片展开（参考 Art Design Pro `--transition-base`） |
| `--zy-duration-slow` | `400ms` | 页面过渡：小织隐藏/恢复、滑入动效（参考 Art Design Pro `--transition-slow`） |
| `--zy-duration-slower` | `600ms` | 长动效：数字滚动、入场动画 |

### 5.2 缓动函数

| Token | 值 | 用途 |
|---|---|---|
| `--zy-ease-linear` | `linear` | 线性：进度条、匀速动画 |
| `--zy-ease-in` | `cubic-bezier(0.4, 0, 1, 1)` | 缓入：元素退出 |
| `--zy-ease-out` | `cubic-bezier(0, 0, 0.2, 1)` | 缓出：元素入场（通用默认） |
| `--zy-ease-in-out` | `cubic-bezier(0.4, 0, 0.2, 1)` | 缓入缓出：呼吸动画、循环动画 |
| `--zy-ease-elastic` | `cubic-bezier(0.34, 1.56, 0.64, 1)` | 弹性：恢复按钮、点赞动效（参考 Leaf Island UI 弹入缓动） |
| `--zy-ease-bounce` | `cubic-bezier(0.68, -0.55, 0.27, 1.55)` | 弹跳：超调效果 |
| `--zy-ease-smooth` | `cubic-bezier(0.22, 1, 0.36, 1)` | 平滑：滑入、页面过渡 |

## 6. 动效类

| 类名 | 效果 | 适用场景 |
|---|---|---|
| `.zy-fade-in` | 透明度 0→1 淡入 | 页面入场、Tab 切换 |
| `.zy-slide-up` | 从下方 20px 滑入 + 淡入 | 卡片列表入场、底部弹窗 |
| `.zy-slide-right` | 从左方 20px 滑入 + 淡入 | 侧边栏展开、轮播切换 |
| `.zy-elastic` | scale 0.8→1 弹性弹入 | 恢复按钮、点赞动效、AI 伴侣出现 |
| `.zy-stagger` | 错峰延迟（`--stagger-i` × 60ms） | 列表项依次入场 |
| `.zy-hover-lift` | 悬浮上浮 2px + 阴影加深 | 可点击卡片（管理端 :hover / 学生端 :active） |
| `.zy-number-roll` | 从下方 10px 滑入 + 淡入 | 数字看板、统计卡片 |
| `.zy-breathing` | 4s 循环 scale 1→1.05 + 透明度 | 疗愈元素、呼吸引导、AI 伴侣待机 |

> 动效类参考 Spark UI 的组件化动效思路：每个类对应一个独立的 `@keyframes`，可叠加使用（如 `.zy-fade-in.zy-stagger` 实现错峰淡入）。

## 7. 参考来源

| 参考项目 | 借鉴内容 |
|---|---|
| [Art Design Pro](https://github.com/Daymychen/art-design-pro) | 圆角 4 档体系（4/8/12/16px）、阴影梯度（sm/md 两级）、动效时长 3 档（150/250/400ms） |
| [Leaf Island UI](https://github.com/leehongxuan/leaf-island-uniapp) | 圆润形态（16px 起步）、极轻阴影策略（rgba 0.04~0.08）、弹入缓动 `cubic-bezier(0.34, 1.56, 0.64, 1)` |
| [Spark UI (vue3-bits)](https://vue3-bits.vercel.app/) | 动效组件化思路：Aurora Text（渐变+clip）、Meteors（绝对定位+@keyframes）、Bento Grid（不等宽 Grid 布局） |

> 注：三个项目的 GitHub raw 文件因沙箱网络限制未能直接访问，Token 值基于任务提供的「已知设计模式兜底信息」整理。待网络恢复后可补充更精确的对照。
