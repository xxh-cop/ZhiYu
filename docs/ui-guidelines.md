# 织愈 UI 规范（2026-10 统一版）

当前适用学生端 zhiyu-app 与管理端 zhiyu-admin-vben/apps/web-ele。下文 v3.0 为历史记录，若有冲突，以本节及 design/tokens.json 为准；旧 zhiyu-admin 不属于本轮改造对象。

- 主色 #2f7566，页面背景 #f5f7f2，卡片 #ffffff，标题 #233d34，次要文字 #586e62，边框 #dfe7dc，浅色区块 #e8efe4，点缀 #bb914d。
- 卡片基础圆角 18px，控件 10px；学生端按移动端比例适配。使用系统无衬线字体；旧 --zy-serif 变量兼容映射到统一字体。
- 学生端侧重陪伴与任务引导，管理端侧重表格、审核及数据密度；统一颜色、控件状态和间距，不强制双端布局完全相同。
- 小织使用现有形象，在首页、开始页、音乐页以内嵌卡片提供入口，不默认以悬浮形象遮挡内容。旧全局悬浮规范不再作为默认要求。
- 音乐不自动播放，离页暂停；上传必须展示进度、取消及失败反馈；视频提供原生控制与失败重试。减少动效偏好应受到尊重。
- 素材必须记录作者、来源、许可证及修改；品类参考图不能冒充课程成品。
- 学生端落点：src/styles/theme.scss、src/App.vue；管理端落点：src/styles/business.scss、src/styles/theme.scss（玻璃与环境光层）、src/preferences.ts。已保存的管理端个性化偏好可能覆盖默认主题。

### 管理端玻璃与动效层（2026-11-01）

借鉴开源脚手架 `scaffold-master` 的「珍珠玻璃」视觉语言，只移植观感，不引入其代码与依赖。

- 令牌：`--zy-glass-*`（表面、模糊、描边、内高光、阴影）、`--zy-ambient`（内容区环境光）、`--zy-radius-card/hero`、`--zy-accent-glow`，全部由 Vben 的 HSL 三元组令牌经 `hsl(var(--x) / a)` 派生，组件内不写品牌色值。
- 深色基调为深墨蓝（`html.dark` 下只压表面明度，主色仍是 indigo `#4F46E5`），Element Plus 与 ECharts 经既有桥接自动跟随。
- 用途边界：玻璃只用于卡片、hero、顶栏与侧栏；`el-table` 行与长列表保持实面，避免逐行 `backdrop-filter` 的滚动开销。
- 登录页：右侧表单放在玻璃面板内（`layouts/auth.vue` 的 `.auth-route`），左侧故事区为漂移光斑 + 掠光 + 艺术图浮动，右上角状态点呼吸；窄屏用 `minmax(0,1fr)` 轨道，避免输入框固有宽度撑出横向溢出。
- 珍珠输入框（`.zy-field` / `.zy-input-shell`，目前只用于登录页）：结构参考 scaffold-master 的 `Field.vue` + `TextInput.vue`——label 与控件同列、错误优先否则 hint、必填星号 `aria-hidden`、尾部清空按钮 `aria-label` 中文且清空后回焦。聚焦改用 `outline: 2px + outline-offset: 2px`（描边转透明），强制颜色模式下仍可见；`is-invalid` 时 outline 转红。超窄屏（≤360px）隐藏清空、保留密码显隐。
- 降级：`prefers-reduced-transparency` 退回实面并关闭环境光；`prefers-reduced-motion` 关闭掠光、悬浮位移与图表入场动画；不支持 `backdrop-filter` 的浏览器自动使用实面。
- 已知坑：Vben 运行时把 `--info` 写成逗号格式（`240, 5%, 96%`），与 `hsl(... / alpha)` 混用会让整条声明在计算期失效，环境光只使用空格分隔的令牌。
- 图表渐变需要 canvas 可用的 rgba，统一走 `#/utils/chart-theme.ts` 的 `withAlpha()`，不要手写色值。

---

# 织愈 · UI 规范（v3.0）

> 适用范围：`zhiyu-admin/`（Vue3 + Element Plus）与 `zhiyu-app/`（UniApp 学生端，H5 / 微信小程序）。
> 本文件由 T0.6a 建立，M1.5 全面修订为冷调薄荷青色板。
> 落地方式：
> - 管理端：`zhiyu-admin/src/styles/variables.scss`（SCSS 变量）→ `zhiyu-admin/src/styles/index.scss`（CSS 自定义属性 + Element Plus 主题覆盖）
> - 学生端：`zhiyu-app/src/uni.scss` 末尾追加同名变量，值必须与管理端一致。
> - 学生端组件库：`zhiyu-app/src/styles/theme.scss`（wot-design-uni CSS 变量覆盖）

## 1. 色板

M1.5 起从暖驼/茶褐色板切换为**冷调薄荷青**色板，与疗愈主题的"沉静、清爽、可呼吸"基调一致。

### 主色

| 名称 | 值 | 用途 |
|---|---|---|
| 薄荷青 | `#2D9B8F` | 主色：CTA 按钮、图标选中态、Logo、链接 |
| 浅薄荷青 | `#5BC4A8` | 渐变辅助：主色到浅色的过渡、hover 态 |

### 背景

| 名称 | 值 | 用途 |
|---|---|---|
| 冷白 | `#F8F9FB` | 页面背景、导航栏背景 |
| 纯白 | `#FFFFFF` | 卡片底色，与冷白背景形成层次 |
| 薄荷青 tint | `#F0F9F7` | 模块底色（薄荷青 4% 透明度叠白） |
| 暖橙 tint | `#FFF8EE` | 模块底色（暖橙 4% 透明度叠白，用于点缀区块） |

### 文字

| 名称 | 值 | 用途 |
|---|---|---|
| 深灰蓝 | `#1F2937` | 标题（替代纯黑，降低视觉刺激） |
| 中灰 | `#4B5563` | 正文 |
| 浅灰 | `#9CA3AF` | 辅助文字、占位符、说明文本 |

### 边框

| 名称 | 值 | 用途 |
|---|---|---|
| 冷灰 | `#E5E7EB` | 卡片描边、分割线、输入框静默描边 |

### 点缀

| 名称 | 值 | 用途 |
|---|---|---|
| 暖橙 | `#FFB547` | 徽章、level 标签、数字高亮 |
| 珊瑚红 | `#FF6B6B` | 点赞、收藏、删除 |

补充：
- **疗愈主题底线**：不使用高饱和红（`#FF0000`）、纯黑（`#000000`）或大面积纯白铺底。
  错误态用珊瑚红 `#FF6B6B`，不用纯红；标题用深灰蓝 `#1F2937`，不用纯黑。
- 危险操作（删除、驳回）用珊瑚红 `#FF6B6B`，配合二次确认弹窗。

## 2. 字体

| 层级 | 字号 | 字重 |
|---|---|---|
| 页面标题 | 22px | 600 |
| 区块标题 | 18px | 600 |
| 正文 | 15px | 400 |
| 辅助文字 | 13px | 400 |

- 行高统一 `1.6`。
- 字族：`'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', sans-serif`。

## 3. 间距

基准 4px；实际只使用以下 5 档：

| 值 | 用途 |
|---|---|
| 8px | 图标与文字之间、卡片内文字与数值 |
| 12px | 表单项内部、按钮组 |
| 16px | 卡片内边距、表单项之间 |
| 24px | 卡片之间、区块之间 |
| 32px | 页面主内容区外边距 |

## 4. 圆角

| 元素 | 值 |
|---|---|
| 卡片 | 12px |
| 按钮 / 输入框 | 8px |

## 5. 阴影

全局只用一种阴影（主色 8% 透明度，保持"纸上浮起一层"的轻柔感）：

```
box-shadow: 0 2px 8px rgba(45, 155, 143, 0.08);
```

不使用大面积深阴影。

## 6. 无障碍与文案红线

- 正文与背景对比度 ≥ 4.5:1（`#1F2937` on `#F8F9FB` 满足）。
- 涉及心理的页面必须出现「本系统不能替代专业心理咨询」声明。
- **禁止**诊断性措辞：「抑郁症」「焦虑症」「你患有」等一律不得出现在任何界面（含错误提示、占位文案）。

## 7. 组件库策略

M1.5 起引入 **wot-design-uni 1.14.0** 作为学生端基础组件库。

### 主题定制

- 主题通过 **CSS 变量覆盖**实现，文件位于 `zhiyu-app/src/styles/theme.scss`。
- 核心变量：
  - `--wot-color-theme: #2D9B8F`（薄荷青，影响按钮/标签/选中态等）
  - `--wot-color-bg: #F8F9FB`（冷白背景）
  - 其他衍生变量随 wot-design-uni 版本更新同步。

### 组件使用规范

| 场景 | 推荐组件 | 说明 |
|---|---|---|
| 按钮 | `wd-button` | 主按钮用薄荷青 `type="primary"`，次按钮用 `type="outline"` |
| 输入框 | `wd-input` | 含校验提示，替代原生 `<input>` |
| 图标 | `wd-icon` | **不再用 emoji** 代替功能图标（emoji 仅限装饰性内容） |
| 卡片 | 优先自研 `.card` / `.zy2-card` | wot-design-uni 无强约束卡片，自研更灵活 |

### Leaf Island UI 弃用

- M1 阶段曾引入 Leaf Island UI 全局样式（`@import '@/uni_modules/leaf-island/index.less'`）。
- M1.5 起因**色板冲突**（暖驼色 vs 薄荷青）弃用，不再引入组件。
- 仓库根的 `leaf-island-uniapp-main` 残留文件待清理（见 M1.5 技术债）。

## 8. AI 伴侣（小织）

### 定位

全局悬浮角色，陪伴用户。出现在所有学生端主页面右下角，可拖动、可点击、可隐藏。

### 文件结构

| 文件 | 职责 |
|---|---|
| `zhiyu-app/src/components/AiCompanion.vue` | UI 组件：形象渲染、拖动、点击、气泡、隐藏/恢复 |
| `zhiyu-app/src/stores/aiCompanion.js` | Pinia store：位置持久化、眨眼/说话状态、气泡内容、隐藏态 |
| `zhiyu-app/src/static/xiaozhi-avatar.png` | 常态：睁眼闭嘴 |
| `zhiyu-app/src/static/xiaozhi-blink.png` | 眨眼：闭眼闭嘴 |
| `zhiyu-app/src/static/xiaozhi-talk.png` | 说话：睁眼张嘴 |

### 交互行为

| 触发 | 效果 |
|---|---|
| 点击（未拖动） | 眨眼 150ms → 弹随机气泡 → 3 秒后自动隐藏 |
| 拖动 | 位置实时更新，松手后持久化到 `localStorage` |
| 拖到底部（`bottom < 80rpx`） | 小织隐藏，出现恢复按钮（🐾） |
| 点击恢复按钮 | 小织回到默认位置（`right: 40rpx, bottom: 160rpx`）+ 淡入 |
| 说话中（`startTalking()`） | 嘴巴动（avatar ↔ talk 每 150ms 切换） |
| 眨眼中再次点击 | 防叠加：`if (currentImage === 'blink') return` |
| 拖动结束后 100ms 内的 click | 抑制（防止浏览器自动 click 触发眨眼） |

### 气泡方向自适应

根据小织距屏幕右边的距离 `position.right` 自动判断：
- 贴右边缘（`right < 100rpx`）→ 气泡朝左展开
- 贴左边缘（`leftEdge < 100rpx`）→ 气泡朝右展开
- 中间区域 → 朝空间更多的一侧展开

### 默认位置

- `right: 40rpx`，`bottom: 160rpx`（rpx 单位，750 设计稿基准）

### 待优化

- 3 张 PNG 素材未对齐（画布尺寸/人物位置不一致），眨眼时有轻微缩小感。CSS 无法根治，需用 Photopea 统一画布尺寸。

## 9. 动效 Token

### duration

| Token | 值 | 用途 |
|---|---|---|
| `--zy-duration-fast` | `150ms` | 微交互反馈：眨眼、按钮点击缩放、气泡入场 |
| `--zy-duration-mid` | `250ms` | 元素显隐：弹窗、Toast、卡片展开 |
| `--zy-duration-slow` | `400ms` | 页面过渡、小织隐藏/恢复淡入 |

### easing

| Token | 值 | 用途 |
|---|---|---|
| `--zy-ease-out` | `cubic-bezier(0.22, 1, 0.36, 1)` | 通用缓出：大部分动画的默认选择 |
| `--zy-ease-elastic` | `cubic-bezier(0.34, 1.56, 0.64, 1)` | 弹性反馈：恢复按钮出现、点赞动效 |
