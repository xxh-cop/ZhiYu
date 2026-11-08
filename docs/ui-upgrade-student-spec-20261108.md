# 织愈学生端 UI 升级实施规格 ·「暖纸青玉」（2026-11-08）

> 配套文档：`docs/ui-upgrade-student-plan-20261108.md`（方案立意与诊断）。本文档是**施工图纸**：数值可直接落地，照此开工。
> 范围：仅 `zhiyu-app`（H5 + 微信小程序）。本规格本身不改任何源码；实施时另起 `feature/*` 分支。
> 视觉对照样张：`output/design/index.html` / `home.html` / `chat.html` / `craft-detail.html`。
> 单位约定：小程序端 WXSS 用 rpx（750 设计稿基准）；H5 端同一套 rpx 由 uni-app 运行时按视口换算，375px 视口下 **2rpx = 1px**。下表「px@375」即 375 视口等效值，仅供评审与样张对照，代码里只写 rpx。

---

## 1. 令牌全表

落地位置：`design/tokens.json`（单一事实源）→ `zhiyu-app/src/styles/theme.scss`（CSS 自定义属性）。下列「操作」列：调=改值，增=新增，留=不动。

### 1.1 色板

#### 品牌色阶（全部不动）

| 令牌 | hex | 角色 | 用于 | 禁用于 |
|---|---|---|---|---|
| `--zy-brand-50` | `#EAF4F1` | 最浅品牌底 | ghost 按钮按下态、浅选中底 | 页面底、卡片底 |
| `--zy-brand-100` | `#C9E4DD` | 浅品牌 | secondary 按钮按下态 | 文本 |
| `--zy-brand-300` | `#5BC4A8` | 图形青 | 吉祥物渐变、三点呼吸、深色态主色 | 文本、按钮底（对比不足） |
| `--zy-brand-500` | `#2D9B8F` | hover/图表 | H5 hover 过渡中间色 | 按钮底（白字 3.39:1 不达标，既定结论） |
| `--zy-brand-600` | `#217E76` | **主色** | 实心按钮底（白字 4.79:1）、图形、图标强调 | **页面底/卡片底上的文字与文字链接（实测 4.33:1 < 4.5，禁用）**、大段文字、整屏铺底 |
| `--zy-brand-700` | `#1A645E` | 深主色 | **文字级品牌色**：链接、ghost 按钮、选中态文字、tint 底上文字（暖纸底 6.16:1 / 卡片 6.82:1）；主按钮按下态 | 深色底上的文字 |
| `--zy-brand-900` | `#0F3D38` | 最深 | 深色态配 brand-300 的文字 | 浅色态使用 |

#### 中性色（本轮调整 + 新增）

| 令牌 | hex | 操作 | 角色 | 用于 | 禁用于 |
|---|---|---|---|---|---|
| `--zy-bg` | `#F6F1E7`（原 `#F5F7F2`） | 调 | 页面画布 | `page` 背景 | 卡片 |
| `--zy-card-bg` | `#FFFDF7`（原 `#FFFFFF`） | 调 | 一级表面 | 卡片、弹窗、输入栏 | 页面底 |
| `--zy-bg-tint-mint` | `#E4EFE7`（原 `#E8EFE4`） | 调 | 平静语义底 | 倾诉主卡、呼吸卡、选中底 | 文本 |
| `--zy-bg-tint-warm` | `#F3E9CF`（原 `#F7EFD9`） | 调 | 非遗/关怀语义底 | 纹样馆卡、基线提醒卡 | 文本 |
| `--zy-title` | `#33302A`（原 `#233D34`） | 调 | 暖墨标题 | 所有标题 | — |
| `--zy-text` | `#5C5648`（原 `#4A5F54`） | 调 | 暖灰棕正文 | 正文、次要、占位提示、免责声明、时间戳 | 标题 |
| `--zy-border` | `#E4DCCB`（原 `#DFE7DC`） | 调 | 发丝线 | 列表分隔线、次级控件描边 | 卡片默认描边（取消） |
| `--zy-stitch` | `#C9BFA9` | **增** | 缝线色 | 虚线分隔、时间线连接、纹样、角饰 | 任何文本、任何交互态唯一指示 |
| `--zy-faint` | `#8A8378` | **增** | 装饰灰 | 仅限英文 eyebrow 大写字标（装饰性、可丢失） | 一切承载信息的文字（对比 3.2:1，不达 4.5:1） |

对比度核对（对 `--zy-bg`）：`--zy-title` 12.6:1；`--zy-text` 6.4:1；`--zy-brand-600` 7.6:1；`--zy-brand-700` 9.4:1。均 ≥ 4.5:1。

#### 语义色（全部不动）

| 令牌 | hex | 用于 | 禁用于 |
|---|---|---|---|
| `--zy-success` | `#2E7D5B` | 成功反馈 | — |
| `--zy-warning` | `#9A6A12` | 仅图标/大字级警示 | 正文（用下一行） |
| `--zy-accent` | `#7A5C12`（2026-11-08 由 `#8A6D1F` 加深） | 非遗 eyebrow、正文级标注（暖金 tint 上 5.16:1） | 图形填充（用 `--zy-gold`）；不作大面积底色 |
| `--zy-warning-text` | `#7A5C12`（与 accent 同源加深） | 正文级警示文字 | 大字/图标级警示（用 `--zy-warning`） |
| `--zy-gold` | `#D9A441` | 徽章、引号、纹样点缀（图形） | 文本 |
| `--zy-care` | `#A64842` | 危机干预入口与卡片、危险操作文本 | 普通错误提示（用 warning 系） |

#### 深色页面组（不动）

`--zy-d-*` 六个令牌保留原值，仅限音乐/呼吸/睡前页面级深色场景，不扩展到全站。

### 1.2 字阶（7 档，新增 `--zy-text-*` 令牌）

| 级名 | 令牌 | 字号 | px@375 | 行高 | 字重 | 字距 | 用途 | 限额 |
|---|---|---|---|---|---|---|---|---|
| display | `--zy-text-display` | 56rpx | 28px | 1.3 | 600 | 2rpx | 首页主视觉、一级落地页主标题 | **每屏 ≤1 次** |
| title-lg | `--zy-text-title-lg` | 40rpx | 20px | 1.4 | 600 | 1rpx | 页面标题、封面展品标题 | 每页 ≤1 |
| title | `--zy-text-title` | 32rpx | 16px | 1.5 | 600 | 0.5rpx | 区块标题、卡片主标题 | — |
| body-lg | `--zy-text-body-lg` | 30rpx | 15px | 1.7 | 400 | 0 | 对话气泡、引言、引导语 | — |
| body | `--zy-text-body` | 28rpx | 14px | 1.7 | 400 | 0 | 默认正文 | — |
| caption | `--zy-text-caption` | 24rpx | 12px | 1.6 | 400 | 0 | 元信息、计数、标签、tab | — |
| micro | `--zy-text-micro` | 22rpx | 11px | 1.6 | 400 | 1rpx | 提示、免责声明、时间戳 | **不允许更小的字** |

- 衬线栈：`--zy-font-display: "Songti SC","STSong","Noto Serif CJK SC","SimSun",serif`，仅 display / title-lg（封面展品标题）/ 引言三类使用；小程序无宋体的机型自然回落 sans，不引入 `wx.loadFontFace`。
- eyebrow 规范：micro 档 + 大写英文 + 字距 4rpx；通用语境用 `--zy-text`，非遗语境用 `--zy-accent`。
- 现状 `--zy-font-xs(20rpx)` 降级为「仅图标内数字」用途，排版不再使用；实施期全文检索替换。
- 落地方式：`theme.scss` 增令牌；`styles/growth.css` 同级新增工具类 `.zy-t-display` … `.zy-t-micro`（类选择器，WXSS 合规）。

### 1.3 间距刻度（沿用现有 7 档 + 新增 1 档）

| 令牌 | 值 | px@375 | 用途 |
|---|---|---|---|
| `--zy-space-2xs` | 4rpx | 2px | 图标与文字间隙 |
| `--zy-space-xs` | 8rpx | 4px | 紧凑元素间 |
| `--zy-space-sm` | 16rpx | 8px | 表单项内部、按钮组 |
| `--zy-space-md` | 24rpx | 12px | 卡片内元素间 |
| `--zy-space-lg` | 32rpx | 16px | 卡片 padding、紧凑段间距 |
| `--zy-space-xl` | 48rpx | 24px | 标准段间距 |
| `--zy-space-2xl` | 64rpx | 32px | 页面底部留白 |
| `--zy-space-3xl` | 72rpx | 36px | **增**：呼吸段（主视觉后、页脚前） |

页面边距：左右 32rpx（`--zy-space-lg`），首页顶部 40rpx。现状各页 28–34rpx 不一，统一为 32rpx。

### 1.4 圆角

| 令牌 | 值 | px@375 | 用途 |
|---|---|---|---|
| `--zy-radius-xs` | 6rpx | 3px | 小标记 |
| `--zy-radius-sm` | 12rpx | 6px | chip 内芯、小缩略图 |
| `--zy-radius-md` | 24rpx | 12px | 输入框、小按钮（保留，控件默认改 full 见组件节） |
| `--zy-radius-card` | 32rpx | 16px | 卡片、气泡 |
| `--zy-radius-feature` | 40rpx | 20px | **增**：主行动卡、纹样大卡、非遗横幅 |
| `--zy-radius-dialog` | 48rpx | 24px | 弹窗 |
| `--zy-radius-full` | 9999rpx | — | 按钮、chip、胶囊输入栏 |
| 气泡折角 | 32rpx 三角 + 8rpx 折角 | — | 对话气泡（见组件 C3） |

### 1.5 阴影（5 档青玉阴影 → 3 档）

| 令牌 | 值 | 用于 |
|---|---|---|
| `--zy-shadow-card` | `0 4rpx 20rpx rgba(72,58,34,0.07)` | 一级卡片、胶囊输入栏 |
| `--zy-shadow-cta` | `0 8rpx 20rpx rgba(33,126,118,0.28)` | 仅限青玉实心主按钮 |
| `--zy-shadow-pop` | `0 16rpx 48rpx rgba(72,58,34,0.14)` | 弹窗、浮层 |

删除：`--zy-shadow-xs/sm/md/lg/xl`（实施后全文检索确认零引用再删）。

### 1.6 描边与缝线

| 项 | 参数 | 用于 |
|---|---|---|
| 发丝线 | `1rpx solid var(--zy-border)` | 列表分隔、次级控件描边 |
| 缝线（横） | `border-top: 2rpx dashed var(--zy-stitch)` | 区块分隔、统计行上下、免责声明上缘 |
| 缝线（竖） | `border-left: 2rpx dashed var(--zy-stitch)` | 时间线连接、引言左边条（引言用 3rpx） |
| 选中环 | `2rpx solid var(--zy-brand-600)` + tint 底 | 心情打卡等单选态 |
| 焦点环 | `box-shadow: 0 0 0 4rpx rgba(33,126,118,0.35)` | `.zy-focused`（见 §4 WXSS） |

### 1.7 纸纹与角饰参数

| 项 | 参数 |
|---|---|
| 纸纹 | 内联 SVG `feTurbulence`，`baseFrequency="0.9"`、`numOctaves="2"`、色值 `rgba(115,102,82,0.05)`、瓦片 240×240rpx；以 base64 data-URI 作 `page` 的 `background-image`（< 2KB，小程序支持 base64 背景）；`prefers-reduced-transparency` 时移除 |
| 纹样角饰 | 内联 SVG 回纹，168×168rpx，`opacity: 0.14`，`--zy-accent` 描边 2rpx，绝对定位于卡片右下（`right:-12rpx; bottom:-12rpx`），`aria-hidden`；仅非遗语境卡片 |
| 封面纹样 | 内联 SVG 经纬双线（青玉 40% + 暖金 30%），仅作占位视觉，真实封面图到位后替换 |

### 1.8 动效（沿用现有令牌，新增 3 条规则）

| 场景 | 参数 |
|---|---|
| 按压 | `transform: scale(0.97)`，`--zy-duration-instant`（100ms），`--zy-ease-out` |
| 选中切换 | 底色/描边 200ms ease-out；选中环 250ms `--zy-ease-elastic` |
| 三点呼吸（流式） | 三点 `scale(.85)→1` + `opacity .35→1`，1400ms 循环，依次延迟 0/180/360ms；reduced-motion 下静态三点半透明 |
| 首页纹样视差 | translateY ≤8rpx，8s ease-in-out 往返；reduced-motion 关闭 |

---

## 2. 组件规格

### C1 `AccessibleButton` v2（改造，沿用基座）

保留现有全部 a11y 逻辑（键盘激活、aria 透传、44px 最小高度）。新增 `variant` 与 `size` props。

**变体**（值均为令牌）：

| variant | 背景 | 文字 | 按下态 | 用途 |
|---|---|---|---|---|
| `primary` | brand-600 + `--zy-shadow-cta` | `#FFFDF7` | brand-700 + scale(.97) | 每屏唯一主行动 |
| `secondary` | tint-mint | brand-700 | brand-100 + scale(.97) | 次行动 |
| `ghost` | transparent | brand-700 | brand-50 | 文字级入口 |
| `danger` | `#F7ECEA` | care | `#F1E0DC` | 删除等危险操作 |
| `care` | care | `#FFFDF7` | `#8F3A35` | 危机卡片主按钮 |

**尺寸**：`lg` 高 96rpx / 字号 32rpx / padding-x 48rpx；`md` 88 / 28 / 40；`sm` 64 / 24 / 28。圆角一律 full。图标圆形档：`icon` 72×72rpx（发送钮）。

**状态**：默认 / 按下（`:active`）/ 禁用（`.is-disabled`，opacity .5，点击拦截沿用）/ 聚焦（`.zy-focused` 焦点环，见 §4）/ 加载（`.is-loading`：内置 28rpx 转圈，`aria-busy="true"`，拦截点击）。
**Hover**：仅 H5 端 `@media (hover:hover)` 内 brightness(1.04)；小程序无 hover，不写。

### C2 卡片工具类 `.zy-card`（新增，纯样式类族）

| 类 | 背景 | 阴影 | 圆角 | padding |
|---|---|---|---|---|
| `.zy-card` | card-bg | `--zy-shadow-card` | card | 32rpx |
| `.zy-card--mint` | tint-mint | 无 | card | 32rpx |
| `.zy-card--warm` | tint-warm | 无 | card | 32rpx |
| `.zy-card--feature` | card-bg | `--zy-shadow-card` | feature | 0（内容自撑，用于封面大卡） |
| `.zy-card--bare` | transparent | 无 | — | 0 |

无描边。选中态由使用方加 `.is-selected`（2rpx brand-600 描边）。

### C3 `ChatBubble`（新增组件，从 chat.vue 抽出）

| 项 | AI（左） | 用户（右） |
|---|---|---|
| 背景 | card-bg + `--zy-shadow-card` | brand-600 |
| 文字 | title 色，body-lg | `#FFFDF7`，body-lg |
| 圆角 | `8rpx 32rpx 32rpx 32rpx`（折角朝头像） | `32rpx 8rpx 32rpx 32rpx` |
| padding | 24rpx 32rpx | 同左 |
| 最大宽 | 74% | 74% |
| 头像 | XiaotuanPet 60rpx | 字母圆 68rpx，tint-mint 底 brand-700 字 |
| 时间戳 | micro，`--zy-text`，上距 10rpx | 同左，右对齐 |
| 流式 | 三点呼吸子组件 `.zy-typing-dots` | — |

气泡与头像底对齐（`align-items: flex-end`）。气泡折角纯色值声明，无伪元素、无小尾巴图形。

### C4 `StitchDivider`（新增，纯样式）

`.zy-stitch-h`（2rpx 横向虚线）、`.zy-stitch-v`（2rpx 竖向虚线，高由内容撑）。`aria-hidden="true"`。

### C5 `SectionHead`（新增，纯样式）

结构：eyebrow（micro/4rpx 字距）+ title 档标题 + 可选右侧 ghost 按钮。下间距 28rpx，上间距由页面节奏档位给（32/48/72rpx）。

### C6 `MoodFace` 心情打卡（改造 home 内联实现）

- 脸：68rpx 正圆，CSS 绘制五官（沿用现有 `.face` 思路，收进组件）。
- 默认：无环；选中（`.is-selected`）：外环 92rpx 直径、`2rpx solid brand-600`、tint-mint 底，200ms 过渡。
- `aria-pressed` 透传（现有逻辑保留）；每个选项最小触控 88rpx。

### C7 `StatStrip`（改造 home `.stats-card`）

去卡片容器：横向均布，数字 title-lg/600/brand-600/tabular-nums，标签 micro，项间 `.zy-stitch-v` 高 60rpx，上下 `.zy-stitch-h`。

### C8 `ChatInputBar`（改造 chat `.input-area`）

容器：card-bg、radius full、padding `8rpx 16rpx 8rpx 32rpx`、`--zy-shadow-card`。内部：输入框（body，placeholder `--zy-text`）+ 语音钮（64rpx 圆，wd-icon `service` 类线性图标，**替换 🎤 emoji**）+ 发送钮（72rpx 圆 primary，仅图标 `↑`，`aria-label="发送"`）。免责声明独立一行于容器下方：micro + `.zy-stitch-h` 上缘。

### C9 `AccessibleDialog`（沿用基座，仅令牌）

面板背景 card-bg、圆角 dialog、阴影 `--zy-shadow-pop`、标题 title 档、遮罩 `rgba(51,48,42,.5)`。焦点圈禁/Esc/回焦逻辑不动。

### C10 `StuckGuideBar` / `StateBlock` / `EmptyArt` / `CompanionNudge`（仅令牌）

背景/文字/边条换中性色新值；StuckGuideBar 左边条保留 6rpx brand-300。形态、频次、aria 逻辑一律不动。

### C11 `XiaotuanPet`（不动）

形象保留。仅首页主视觉与对话空状态以 224rpx / 220rpx 大尺寸使用（现尺寸 prop 已支持）。

---

## 3. 页面级改动清单

「逻辑」列 = 是否触碰业务逻辑/接口/状态机。全部页面结论：**无逻辑改动**。

### 3.1 首页 `pages/home/index.vue`（对照 `home.html`）

| 区块（现类名） | 改动 | 依赖 | 逻辑 |
|---|---|---|---|
| `.home-header` | 右侧加入通知铃铛胶囊（未读数红点，点击进入通知页，数据复用现有 `noticeState`）；头像钮不变 | C1 icon 档 | 否（模板移动，数据同源） |
| `.hello-row` | 单行问候，caption 档，右侧「你好，xx ↗」删除（与头像重复） | 字阶 | 否 |
| `.home-hero` | 去卡片容器，直落纸底；标题 display 档衬线；主图改 XiaotuanPet 224rpx + 纹样视差；CTA 改 primary lg | C1、C11、§1.7 | 否 |
| `.action-grid`（三行动卡） | 改为 1 主 2 次：主卡 `.zy-card--mint`+feature 圆角，内嵌小织；两次卡 `.zy-card` | C2 | 否 |
| `.pet-card` | **删除**，入口并入主卡 | — | 否 |
| `.mood-card` | 换 C6；选中回应语保留 | C6 | 否 |
| `.stats-card` | 换 C7 | C7 | 否 |
| `.nudge-card` | 换 `.zy-card--warm`，文案/频次不动 | C2 | 否 |
| `.calm-grid` | 两卡改 `.zy-card--mint` / `.zy-card--warm`，♫ emoji 改 wd-icon | C2 | 否 |
| `.notice-card` | **删除**，能力并入页头铃铛 | — | 否 |
| `.craft-row` 推荐列表 | 首条改 16:10 封面大卡 `.zy-card--feature`，其余保留行式但去底部分隔改 `.zy-stitch-h` | C2、C4 | 否 |
| `.daily-note` | 去容器，居中衬线引言 body-lg，上下缝线 | C4 | 否 |
| `.heritage-entry` | 全宽 `.zy-card--warm` + 回纹角饰 | C2、§1.7 | 否 |
| `.explore-links` | 三链接并入页脚文字行，缝线分隔 | C4 | 否 |
| `.home-foot` | 免责声明 micro 档（原等效 18rpx → 22rpx） | 字阶 | 否 |

区块顺序：页头 → 问候 → 主视觉 → 缝线 → 行动区 → 打卡 → 统计行 → 新东西 → 寄语 → 非遗 → 页脚。13 → 10 个可视块（其中 3 个为无容器块）。

### 3.2 情绪对话 `pages/emotion/chat.vue`（对照 `chat.html`）

| 区块 | 改动 | 依赖 | 逻辑 |
|---|---|---|---|
| `.chat-header` | 去底部分隔线；小织 SVG 76rpx；「寻求支持」改 danger 变体 sm（绛红文字，保持常驻） | C1 | 否 |
| `.welcome` 空状态 | 小织 220rpx + display 衬线问候 + 3 个开场白 chip（缝线描边胶囊，点击仅 `inputText` 回填，静态文案） | C4 | **新增前端交互，无接口** |
| `.bubble` | 换 C3；圆角 px 混用修复（16px → 令牌） | C3 | 否 |
| `.typing` | 渐变竖条 → 三点呼吸 | C3 | 否 |
| `.recommend-entry` | 改 tint-mint 胶囊 + brand-700 文字 | C2 | 否 |
| `.crisis-card` | 仅令牌：底色 `#F7ECEA`、主按钮走 C1 care | C1 | 否 |
| `.local-note` | 仅令牌 | — | 否 |
| `.input-area` | 换 C8；🎤 改图标 | C8 | 否 |
| `.footer-disclaimer` | micro 档 + 缝线上缘 | C4 | 否 |

### 3.3 手作详情 `pages/craft/detail.vue`（对照 `craft-detail.html`）

| 区块 | 改动 | 依赖 | 逻辑 |
|---|---|---|---|
| `.cover-section` | 去圆角小卡改通栏 16:10（宽 100%，高 62.5vw 上限 500rpx）；底部渐变罩 `linear-gradient(180deg, rgba(51,48,42,0) 34%, rgba(51,48,42,.52))`；标题 title-lg 衬线叠罩 | §1.7 | 否 |
| `.share-craft` | 收到封面右上 68rpx 圆形浮钮（与返回钮同级） | C1 icon | 否 |
| `.info-pills` | 三 pill 合并为一行 caption：`编织 · 入门 · 约 20 分钟`，间隔点用 stitch 色 | 字阶 | 否 |
| `.therapy-tags` | 改 chip 流（`.zy-card` 内芯 chip：card-bg + shadow-card + radius full + caption） | C2 | 否 |
| `.inheritor-bar` | 并入 meta 行（`传承人 王秀英 示范`，caption，点击区保留） | 字阶 | 否 |
| `.section-card`（文化背景） | 改引言版式：左 3rpx 缝线竖条 + 52rpx 衬线引号（gold）+ body-lg 衬线正文 + micro 出处 | C4 | 否 |
| `.section-card`（材料） | `.material-grid` 改 chip 流 | C2 | 否 |
| `.step-timeline` | 圆点 52rpx；连接线 `border-left: 2rpx dashed var(--zy-stitch)`；当前步 `.is-current`：brand-600 实心 + `box-shadow: 0 0 0 10rpx rgba(33,126,118,.12)` | C4 | 否 |
| `.bottom-bar` | 保持 fixed 定位（小程序 sticky 不可靠，**禁用 sticky**）；主按钮 primary lg full 圆角；上方 micro 安全提示 | C1 | 否 |
| `.footnote` | micro 档 | 字阶 | 否 |

### 3.4 作品社区 `pages/work/list.vue`

- `.brand-header`：标题 title-lg，副标 caption。
- `.filter-tab`：选中态 `.is-selected` 从底填充改为 brand-600 文字 + 底部 2rpx 缝线虚线（brand-600 色），200ms 过渡；`aria-selected` 逻辑不动。
- `.work-card`：封面 4:3（高 = 宽×75%），radius feature 上图下卡；`.card-message` 衬线 body；`.card-meta` 一行 caption（心/评图标保留 wd-icon，取消颜色反转，liked 态仅心形填 care 色）。
- 骨架屏 `.zy-skeleton` 色值换暖系（`#E9E2D2 / #F3EEE2`）。

### 3.5 我的 `pages/profile/index.vue`

- `.profile-card`：去描边，`.zy-card`，顶部 accent 条保留并改为 4rpx 渐变 `linear-gradient(90deg, #217E76, #D9A441)`。
- `.stats-row`：三格改 C7 同款（去白卡）。
- `.badge-card.locked`：改 `.is-locked`：透明底 + 2rpx dashed stitch 边框 + 图标 opacity .4（「还没绣上」）。
- `.menu-icon-wrap`：底色由实心彩块改 tint-mint，图标色 brand-700；`tone-*` 修饰类仅保留暖金/薄荷两族。
- `.boundary-card`：`.zy-card--warm`。

---

## 4. WXSS 合规

| 禁用写法 | 本规格的等价方案 |
|---|---|
| `*` 通配 | 沿用现状枚举选择器（`motion-reduced` 模式），新增样式不引入通配 |
| 属性选择器 `[disabled]` `[aria-*]` | 状态一律类名：`.is-disabled` `.is-selected` `.is-current` `.is-locked` `.is-loading` `.is-pressed`（沿用既有 `is-*` 约定） |
| `:focus-visible` | 双端统一 `.zy-focused`：基座组件内 `@focus`/`@blur` 切换该类，挂焦点环（§1.6）；H5 端额外保留 `:focus-visible` 作渐进增强（H5 是真 CSS，合法；小程序端该规则被忽略，无报错） |
| `:nth-child()` | 显式索引类（沿用 home 现有 `'face-'+i` 模式） |
| `:not()` `:last-child` | 显式尾项类 `.is-last`，或改为每项统一样式（时间线连接线用每项 `border-left` + 首项定位规避） |
| `position: sticky` | 禁用；底部固定栏沿用现有 fixed 方案 |
| `backdrop-filter` | 不用（小程序不支持） |
| `gap` | 现状已用且基础库 ≥2.20 支持，继续可用；关键布局兜底用 margin |
| 新增 keyframes | 类选择器触发；reduced-motion 走现有枚举式 `.motion-reduced` 覆盖 |

## 5. 双端差异表

| 项 | 微信小程序 | H5 |
|---|---|---|
| 单位 | rpx 原生 | rpx 由 uni-app 按 750 基准运行时换算（375px 视口 2rpx=1px） |
| 衬线 display | iOS Songti / 安卓回落 sans | Win SimSun / macOS Songti；均可读，差异可接受 |
| 焦点环 | `.zy-focused`（JS 切换） | `.zy-focused` + `:focus-visible` 双保险 |
| hover | 无，不写 | `@media (hover:hover)` 内 brightness(1.04) / 卡片上浮 2rpx |
| 纸纹背景 | base64 data-URI（< 2KB） | 同一 data-URI |
| 按压反馈 | `:active` | `:active` + hover 分层 |
| 底部固定栏 | fixed + `env(safe-area-inset-bottom)` | 同左 |
| SSE/接口 | 无变化 | 无变化 |

---

## 6. 分阶段实施计划

每阶段独立 commit（`feature/ui-warm-paper` 本地分支），回滚 = `git revert` 对应阶段 commit。全部阶段结束后才合并。

| 阶段 | 范围 | 工时 | 进入下阶段的验证条件 | 回滚 |
|---|---|---|---|---|
| **P0 基线** | 8 个关键页面双端现状截图存档至 `output/playwright/baseline/`；建分支 | 0.5 人日 | 基线截图齐 8 页 | 删分支即可 |
| **P1 令牌** | tokens.json neutral 节 + theme.scss 字阶/阴影/缝线/纸纹令牌 + 工具类 `.zy-t-*` `.zy-card*` `.zy-stitch-*` | 1 人日 | 双端构建通过；node 脚本核对 8 组前景/背景对比度全部 ≥4.5:1；基线截图对比无「文字消失/对比骤降」 | revert 本阶段 commit |
| **P2 组件层** | C1 按钮变体、C3 气泡、C4–C8；`.zy-focused` 焦点方案 | 1.5 人日 | 双端构建通过；首页/对话页走查：键盘 Tab 全元素可达、焦点环可见；按钮变体在至少 2 页实装 | revert |
| **P3 首页** | §3.1 全部区块 | 1 人日 | 双端真机/模拟器走查：推荐、统计、nudge、未读数渲染与基线一致；reduced-motion 下纹样视差关闭；无横向溢出 | revert |
| **P4 对话页** | §3.2 | 0.5 人日 | SSE 流式回归（发一条消息看到三点呼吸→气泡流式→完成）；危机卡可触发可关闭；chip 回填可用 | revert |
| **P5 手作详情 + brand-600 收敛** | §3.3 + 附录 A 全表（含长尾 105 处） | 1.5 人日 | 封面/步骤/材料渲染；「开始创作」跳转会话页正常；fixed 底栏不遮内容；**附录 A 非豁免项复扫为零** | revert |
| **P6 社区+我的** | §3.4、§3.5 | 1 人日 | 列表加载/空/错三态走查；徽章 locked 态、菜单跳转正常 | revert |
| **P7 一致性债** | growth.css 色值走变量、px/rpx 统一、emoji 图标替换、旧阴影令牌删除 | 1 人日 | 全局检索：源码无 `:focus-visible`、无 `#217e76` 等裸 hex（令牌文件除外）、无 `🎤🔊` 等功能 emoji；双端构建通过 | revert |
| 合计 | | **8 人日**（含每阶段双端构建与走查） | | |

**答辩前 / 后划分**：

- **答辩前做 P0–P5**（4.5 人日）：演示主路径是 首页 → 对话 → 手作详情 → 创作会话，前四个阶段恰好覆盖这条链路的全部观感；P1 令牌是全站改观的杠杆，必须最早。
- **答辩后做 P6–P7**：社区/我的不在主演示路径；P7 是全站清扫，触碰面最大（12+ 个 growth.css 页面），回归成本高，答辩前做会挤占主路径验证时间。
- 创作会话页 `craft/session` 不在本轮范围：它涉及心流态/卡点引导等状态机样式，单独立项（见不做清单）。

### 基线存档记录（P0 执行实录）

- 对应 commit：`2593d0de6fdf8f74b47bfed24bcc2c99efe488d6`（`2593d0d`，P0 采集时工作区另有他人未提交改动，基线以其时的磁盘状态为准）。
- 采集时间：2026-11-08 11:40（Asia/Shanghai）。
- 采集环境：后端本地 jar（dev profile）+ MySQL/Redis 本机实例；H5 dev server（vite :5173）；无头 Chrome 156，视口 375×812@2x，整页截图；登录账号 student01（种子数据）。
- 存档位置：`output/playwright/ui-baseline-20261108/`，命名 `h5-{page}-p0.png`，元数据 `meta-p0.json`。
- 覆盖页面（5 页，按本任务用户口径）：首页、情绪对话、手作详情（id=940001）、作品社区、我的。
- P0 构建与闸口：H5 build ✓；mp-weixin build ✓（有既有 circular chunk 警告）；三道闸口全绿（wildcard ✓ / isclass ✓ / elements ✓ 50 条既有死规则警告）。
- 小程序端基线（2026-11-08 补采成功）：微信开发者工具位于 `D:\develop\WeChat_develop\微信web开发者工具`，经 `cli.bat auto --project dist/build/mp-weixin --auto-port 9420` + 裸协议脚本（`.ai-tmp/mp-raw-shoot.cjs`，App.callWxMethod/App.callFunction/App.captureScreenshot）采集，命名 `mp-{page}-p0.png`，同目录。

### P1 执行实录（2026-11-08）

- 落码：`design/tokens.json`（neutral 暖化 + stitch/faint + radiusFeature/space3xl/shadow 三档/字阶七档；accent.goldText 与 semantic.warningText 加深为 #7A5C12）、`theme.scss`（同步 + 新增令牌）、新增 `src/styles/zy-toolkit.scss`（字阶/卡片族/缝线/焦点环/纸纹）、`App.vue` 引入、新增 `scripts/check-contrast.js`（14 组，挂 `pnpm check:contrast`）。
- 规格修正（对比度闸口当场拦住）：brand-600 文字级用途改划给 brand-700；accent #8A6D1F→#7A5C12（14 组实测全绿）。
- 验证：H5 build ✓ / mp-weixin build ✓；三道闸口全绿（elements 仅 50 条既有死规则警告）；check-contrast 14/14 通过；H5 五页 + mp 五页 P1 截图（`h5-{page}-p1.png` / `mp-{page}-p1.png`）逐页复核：无横向溢出（scrollWidth=375）、无控制台异常、无文字截断重叠。

### P2 执行实录（2026-11-08）

- 落码：`AccessibleButton` 加 `variant`（primary/secondary/ghost/danger/care）× `size`（lg/md/sm/icon）+ `.zy-focused` 焦点环（@focus/@blur 切换，双端生效）；默认 `variant=''` 无样式，既有页面零视觉变化（H5 P2 五页截图与 P1 逐页一致）。新增 6 组件：`ChatBubble`（折角气泡/三点呼吸）、`StitchDivider`、`SectionHead`、`MoodFace`、`StatStrip`、`ChatInputBar`（wd-icon service 替换 🎤）。
- 证据页：`pages/dev/ui-kit.vue`（注册进 pages.json，标题「组件样张（开发）」），全变体全状态渲染；H5 整页截图 `h5-dev-ui-kit-p2.png`，小程序端首屏 `mp-dev-ui-kit-p2.png` + 分段 `mp-dev-ui-kit-{750,1550,2350}-p2.png`。
- 闸口：wildcard ✓（97 文件）/ isclass ✓（94 文件；`.zy-btn.is-disabled` 曾因模板无字面量被拦，已改为模板内联字面量）/ elements ✓ / contrast 14/14。
- 既有页面回归：H5 五页 P2 截图与 P1 视觉一致；mp 五页一致。

---

## 7. 风险与不做清单

### 风险与对策

| 风险 | 等级 | 对策 |
|---|---|---|
| 暖底迁移期两色调并存（阶段中停） | 中 | P1 单独可合并：令牌先全量切换，页面结构后改；任何中间态下品牌色不变，不会「两半不像一个产品」 |
| 小程序 base64 背景图兼容性 | 低 | 纸纹为增强项：加载失败仅表现为纯色暖底，无破版；真机验证不通过则全局关闭纸纹（一处开关） |
| 衬线在安卓回落不一致 | 低 | display 级 Sans 回落版同样式预检；接受差异（字重/字距不变） |
| 审美评审主观 | 中 | 以 `output/design/` 样张为评审基准，先确认样张再开工；开工后不允许超样张范围自由发挥 |
| 触碰首页模板引发 nudge/推荐回归 | 中 | P3 验证条件含三接口渲染核对；区块移动不改数据流 |
| 开场白 chip 被误读为「AI 建议」 | 低 | chip 文案为第一人称自述（「最近静不下心来」），静态固定 3 条，不标注 AI 来源 |

### 不做清单

1. 不改品牌色阶与语义色（青玉案 2026-11-02 定案不动）。
2. 不做全站暗色模式（保留音乐/呼吸页局部深色现状）。
3. 不新增吉祥物、不改小织形象；AiCompanion（PNG 悬浮旧形象）下线与否另立任务。
4. 不接在线字体、不用 `wx.loadFontFace`。
5. 不做玻璃拟态/3D/粒子/渐变大 hero 等科技感效果。
6. 不动管理端（`zhiyu-admin-vben`）。
7. 不动 `craft/session` 创作会话页（心流/卡点状态机样式另立项）。
8. 不动任何接口、文案策略、nudge 频次闸门、危机干预流程；免责声明位置与可读性不退步。
9. 不做小程序自定义 tabbar 皮肤（保留现有 tabbar 配置）。
10. 本规格评审通过前，不改 `zhiyu-app/src` 任何源码。

---

## 附录 A · brand-600 文字用途收敛清单（2026-11-08 全量扫描，`.ai-tmp/brand600-scan.cjs`）

口径：仅 `color:` 文本用途（border/background 图形用途合规不计）。**总计 133 处 / 53 文件**。P5 结束时清零（豁免项除外）。
豁免判定：大字级（≥40rpx 且 600）对比度要求仅 3:1，brand-600 实测 4.33 已满足；`↗` 等箭头字符视为图形。

| 归属 | 文件（行号） | 处数 |
|---|---|---|
| **P3 首页** | pages/home/index.vue: 497,513,527,532,559,563 | 6（另 541,573,586 为 ↗ 图形，拟随卡片改造去掉箭头字符） |
| **P4 对话页** | pages/emotion/chat.vue: 375,435,515,617,634,662,772 | 7 |
| **P5 手作详情** | pages/craft/detail.vue: 357,430 | 2 |
| **P5 收敛·社区/我的相关** | work/list.vue: 403,416,557；work/mine.vue: 36；work/favorites.vue: 40；work/work-detail.css: 7,33,44,68,74,84；work/soft-appeal.vue: 293；profile 页无（已用 accent） | 13 |
| **P5 收敛·全局长尾**（growth.css 代与其余页面/组件） | growth.css: 2,3,18；craft/recommend.vue 6；craft/session.vue 4；sub-heritage/studio.vue 13；sub-heritage/detail 2、gallery 1；inheritor/list 5、detail 3、story 2；practice/short 5；settings/memory 4、settings/index 1；start 4；materials/index 2、kits 4；ai/rag-ask 6；emotion/notes 1、note-edit 1、history 1；components 长尾（AiCompanion/BreathingAnimation 2/ChatPanel 3/CompanionNudge/CompanionPanel 3/ListeningSummaryCard 2/MediaVideo/PracticeSessionBar 2/PrivacyConsent/SensoryPractice/XiaozhiCard）；assessment/drawing/journey/music/notification/privacy/tracing/group/crisis 各 1–2；sub-programs 2；App.vue 1 | 105 |
| **豁免（大字级/图形）** | components/StatStrip.vue: 31（40rpx 600 大字）；pages/support/index.vue: 18（42rpx 600 电话号）；各 ↗ 箭头字符 | 不计入清零 |

> P3–P4 验收时把对应行从本表划去；**P5 结束时本表非豁免项必须为零**（复扫脚本 `.ai-tmp/brand600-scan.cjs` 输出 total=0，或仅余豁免项）。
