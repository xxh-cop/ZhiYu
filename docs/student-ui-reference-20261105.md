# 学生端 UI/AI 交互参考调研（2025–2026 GitHub 高星项目迁移评估）

- 调研日期：2026-11-05
- 适用范围：`zhiyu-app`（UniApp + Vue 3.4 + Pinia 2 + Vite 5 + Wot Design Uni，H5 + 微信小程序双端）；`zhiyu-admin-vben`（Vue 3.5 + Element Plus 2 + ECharts 6）仅作顺带参考
- 数据纪律：star 与活跃度均标注来源与快照日期，核不到的写「未核实」，不使用训练记忆里的旧数字

---

## 1. 结论先行：最该抄的 5 招

1. **抄 assistant-ui 的对话分层与流式状态语义**（消息分区/流式光标/中断与重试的固定位置/工具状态条）→ 落在学生端 `pages/ai` 对话页 → 等待焦虑可预期化，中断可控，与既有 SSE「句级缓冲 A 方案 + chunk/revoke/done 三事件」协议零冲突，纯前端重排即可。
2. **抄 tweakcn/shadcn 的「令牌单源」工程法** → 把学生端 `docs/design-tokens-20261102.md` 落成一份 CSS 变量/scss 变量单源，H5 与小程序都从这里出值 → 后续任何改版（含管理端已验证的 ZyListPage 路线）成本骤降，这是所有后续借鉴的地基。
3. **从 Magic UI / Cult UI 里只挑 2–3 个「静态质感」纯 CSS 效果**（shimmer 骨架、柔光渐变描边、细噪点/点阵背景）→ 落在首页三行动卡、作品卡、空态插画容器 → 不引库、小程序可降级、每个效果半天内完成，视觉收益直接。
4. **抄 CopilotKit 的 HITL「确认前停顿」节奏** → 发布作品、报名活动、撤回情绪便签等敏感操作前用一张温和确认卡（说清后果 + 可返回），替代连续弹窗 → 对情绪敏感用户是硬收益，与 Q 批「审核中」文案体系一脉相承。
5. **抄心情记录类产品的「低门槛 + 无评判」记录范式（moodiary/EMMO 形态）+ 纯 CSS 呼吸圆** → 落在 `pages/emotion` 情绪便签与 `pages/mindfulness` 心念练习 → 记录动作一步完成、不做 streak 火焰竞赛化呈现；呼吸圆用 CSS transform scale 实现，H5/小程序双端可行。

> 一句话总原则：这批高星项目几乎全是 React + Tailwind 生态，**代码一律不搬，只搬「信息层级、交互节奏、状态语义」三样东西**；所有动效以「低刺激」为默认基线。

## 2. 分级统计

| 等级 | 含义 | 数量 |
| --- | --- | --- |
| A 类｜设计思路可借鉴 | 7 项（shadcn/ui 生态、tweakcn、assistant-ui、CopilotKit、Lobe Chat、Cult UI、moodiary 及心情 App 形态） |
| B 类｜纯前端技术可复刻 | 5 项（Magic UI 精选效果、View Transitions、Streamdown/remend 思路、Motion 动效参数哲学、呼吸引导圆） |
| C 类｜代码可直接搬 | 0 个整库；仅 2 个片段级（reduced-motion 兜底块、shimmer 骨架 CSS，已在 B 类内给出） |
| D 类｜不可迁移 | 9 项（Vercel AI SDK、Lobe UI、Motion 主库、@vueuse/motion、Aceternity、Flowise、openui、Dify、Tailwind v4 学生端） |

## 3. 候选清单表

| 项目 | star 量级（快照） | 2025–2026 活跃度 | 迁移等级 | 一句话价值 | 小程序可行性 |
| --- | --- | --- | --- | --- | --- |
| shadcn/ui（shadcn-ui/ui） | ≈127K（2026-11 快照，见来源） | 活跃（2026-11 仍有发布） | A | 「拥有代码 + 设计令牌」的组件哲学，印证令牌单源路线 | 不涉及（React/Tailwind，仅借思路） |
| tweakcn（jnsahaj/tweakcn） | 10,493（API 快照 pushed 2026-09-03） | 活跃 | A | 主题令牌→实时预览→导出的编辑器流程，管理端主题治理可参照 | 不涉及 |
| assistant-ui（assistant-ui/assistant-ui，原 Yonom） | ≈12.9K（官网实时页 2026-11；ghtrends 2026-10-29 为 12,812） | 活跃（npm 周下载 2.8M） | A | AI 对话 UX 状态机与消息分层的最成熟公开参照 | 不涉及（React），思路双端可用 |
| CopilotKit（CopilotKit/CopilotKit） | ≈38K（agentindex 2026-10-27 为 38.3k） | 活跃 | A | Human-in-the-loop 确认卡、生成式 UI 的节奏控制 | 不涉及（React SDK），思路双端可用 |
| Lobe Chat（lobehub/lobe-chat） | ≈70K（lobehub 官方数据 2026；v2.3.0 于 2026-11-01 发布） | 活跃；**许可证已变更为 LobeHub Community License** | A | 对话信息架构（话题管理/助手与会话分离）参照 | 不涉及（Next.js），思路可用 |
| Cult UI（nolly-studio/cult-ui） | ≈6.8K（ross.abutalabs 观测 2026-10-27 为 6,758）MIT | 活跃（官网组件 2026-10 更新） | A | AI 输入框层级设计；其 shader 组件「离屏/减少动态即暂停」的工程实践 | 不涉及（React），实践可学 |
| moodiary（ZhuJHua/moodiary） | 2.1K（HelloGitHub 快照）AGPL-3.0 Flutter | 活跃 | A | 心情日历/低门槛记录/本地优先的产品范式 | 不涉及（Flutter），思路可用 |
| Magic UI（magicuidesign/magicui） | 22,688（API 快照 pushed 2026-10-11） | 活跃 | B（精选 2–3 个效果） | 无组件库依赖的「CSS 质感」做法，少数效果可纯 CSS 复刻 | 部分：shimmer 可以；渐变光环需降级（见 §5） |
| View Transitions API（平台能力，非 repo） | — | Chrome 111+/Safari 18+/Firefox 144+（MDN，Interop 2026 继续推进跨文档） | B（仅 H5 渐进增强） | 页面切换的低成本平滑过渡 | 不可用（无 DOM），H5 独享 |
| Streamdown / remend（vercel/streamdown） | star 未核实 | 活跃（v2.5 于 2026-03 发布） | B（仅借思路） | 流式 Markdown「半截语法」的修复与延迟渲染规则 | 思路可用；我们句级缓冲已规避大半 |
| Motion（motiondivision/motion） | 34,816（API 快照 pushed 2026-11-02） | 活跃 | B（仅借参数哲学） | spring/stagger 的时长-延迟配比可直接抄成 CSS | 不涉及（DOM/WAAPI 库） |
| 呼吸引导（breathly 等开源实现） | star 未核实；维护趋缓 | 弱 | B | 4-7-8/箱式呼吸的视觉引导节奏 | 可以（纯 CSS transform 方案） |
| Vercel AI SDK（vercel/ai） | ≈27.7K（ai-sdk.dev 2026-11） | 活跃 | D | 后端已定 Spring SSE 方案，不引 TS SDK；其状态机命名已在 A 类被吸收 | 不可用 |
| Lobe UI（lobehub/lobe-ui，@lobehub/ui） | ≈1.4K（镜像快照，**未核实**）；npm 发布极活跃（检索当日有发布） | npm 活跃 | D | React + antd 系组件库 | 不可用 |
| @vueuse/motion（vueuse/motion） | star **未核实** | 趋缓迹象（**未核实**） | D | DOM 动画库，小程序无 DOM；且「不引新库」纪律 | 不可用 |
| Motion for Vue（motiondivision/motion-vue，npm motion-v） | star **未核实**；npm 2.10.0 检索前一日发布 MIT | 活跃 | D（小程序）/拍板项（H5） | Vue 原生声明式动效，H5 唯一值得考虑的动效库 | 不可用（依赖 DOM/WAAPI） |
| Aceternity UI | 非开源组件库（站点 registry 模式，2026-11-03 有更新） | 活跃（网站） | D | React 18+/Next/Tailwind v4/Framer Motion 栈，且多为高频炫技 | 不可用，且风格不符红线 |
| Flowise（FlowiseAI/Flowise） | ≈55.5K（gstars 2026-10-15） | **2026-07-29 官方宣布 code freeze，停止新功能开发** | D | 工作流编排器，非我们形态；高 star≠活跃的教学案例 | 不可用 |
| openui（wandb/openui） | ≈22.5K（dev.co 快照） | 活跃度**未核实**（2024 爆红后趋缓迹象） | D | 「描述生成 UI」工具，与产品形态无关 | 不可用 |
| Dify（langgenius/dify） | ≈158–160K（gstars 2026-10-30） | 活跃 | D | 整套 LLM 平台；其链路可视化我们已由 `ai_trace` + 管理端 AI 治理页覆盖 | 不可用 |
| Tailwind CSS v4（tailwindlabs/tailwindcss） | ≈98K（gstars 2026-10-28） | 活跃（Tailwind Labs 2026 年并入 Shopify，维护延续） | D（学生端） | 学生端用 Wot Design Uni + scss，引入需 weapp-tailwindcss 适配层，违反「不引新库」；管理端 Vben 已内置 Tailwind 无需动作 | 需适配层，不引入 |

## 4. 小程序硬约束核实结论（影响所有 B/C 类方案）

- `zhiyu-app/src/manifest.json` **未配置 Skyline**，小程序走默认 WebView 渲染。
- **`backdrop-filter` 不可用**：WebView 内核不支持（写入静默失效，社区实测）；Skyline 下仅部分支持且官方文档明示「不支持 multi function / drop-shadow / url；与 opacity 混合有问题；blur 某些情况表现不一致」，另有 iOS Skyline 下与 fixed overlay 冲突导致 canvas 黑块的已知案例。→ 毛玻璃一律用「半透底色 + 细边框 + 轻阴影」降级。
- **内联 SVG / SVG filter 不可用**：小程序 `<image>` 不支持 SVG（项目既有事实，小织已用 PNG 帧降级）。
- **`prefers-reduced-motion` 媒体查询小程序不可用**：WXSS 媒体查询不含此项（H5 有效）。→ 小程序端以「低动效为默认基线 + 设置页手动开关」兜底，与 R 批小织「小程序静态帧降级」的既有决策一致。
- **复杂渐变限制**：`conic-gradient` 在小程序 WebView 不可用（本次未专项真机核实，按保守处理）；`linear/radial-gradient` 可用。渐变光环类效果只允许 H5 增强、小程序静态渐变降级。
- `position: sticky` 在小程序基础库 2.7.1+ 可用（本次未专项核实，建议真机抽查后再用于关键路径）。
- 主包预算：T 批后主包约 1.57MB、余量约 1.7–1.9MB（含后续增长），所有方案均按「零新增位图资产」设计。

## 5. 逐条可落地方案（A/B 类）

### A1. assistant-ui 的对话分层与流式状态语义（A 类，优先级最高）

- **抄什么**：①消息列表/输入区/状态条三层分离；②流式期间输入区右侧固定「停止」位，结束后原位变「重新生成」；③每条 AI 消息底部常驻轻量操作行（复制/重发），不 hover 才出现（触屏无 hover）；④工具调用渲染为一条「正在做的事」状态条（如「正在整理你的情绪线索…」），完成后自动折叠。
- **落在哪**：`pages/ai` 对话页（倾听链 chat；companion 编排层收敛前只改呈现层）。
- **怎么做**：重排现有消息组件结构，状态映射到既有 SSE 的 chunk/revoke/done 事件与调度层 done 新增的 `capability` 字段；工具状态条先消费已有事件，不做协议扩展。
- **成本**：约 2–3 天（含双端构建验证与 320px 抽查）。
- **风险**：两套对话并存（`/api/emotion/chat` 与 companion），只改共同组件，避免提前触碰收敛问题；「capability 可视化」不得泄露调度细节，文案用人话。

### A2. tweakcn/shadcn 的令牌单源工程法（A 类，地基）

- **抄什么**：令牌（色/间距/圆角/动效时长）单源定义 → 全端消费 → 改一处全端生效。
- **落在哪**：学生端新增 `src/styles/tokens.scss`（或复用既有变量文件收口），把 `docs/design-tokens-20261102.md` 的 #217E76 主色、亮青 #5BC4A8、深青 #1A645E、暖金 #D9A441 与警示双轨色落成变量；新增颜色必须先加令牌。
- **成本**：1–2 天梳理 + 随后续页面改造逐步迁移（不做一次性全量替换）。
- **风险**：老页面硬编码色存量多，按「碰到的页面顺手迁」策略，避免大改引发回归；管理端 Y 批已完成硬编码清零，学生端可对齐该做法。

### A3. CopilotKit 的 HITL 确认前停顿（A 类）

- **抄什么**：破坏性/公开性动作前给一张「说人话」的确认卡：动作后果、可逆性、返回路径，主按钮措辞具体（「发布，审核通过后公开」而非「确定」）。
- **落在哪**：`pages/work` 发布流程、`pages/practice` 活动报名、`pages/emotion` 便签删除、`pages/privacy` 隐私撤回（Q 批已埋引导态，可统一升级）。
- **成本**：每处 0.5 天以内，复用既有弹层组件。
- **风险**：确认卡每流程最多一张，禁止连续弹两张；文案维持「未通过审核 + 原因 + 可修改引导」的非审判语气。

### A4. Lobe Chat 的对话信息架构（A 类，轻量借鉴）

- **抄什么**：历史会话列表的「静音存在感」（弱视觉权重、可滑出收起）、新对话入口的固定位置、空会话时的引导话术不超过两条。
- **落在哪**：`pages/ai` 会话列表页。
- **成本**：1 天。
- **风险**：不要顺手引入「助手市场/角色卡」形态——与「陪伴引导、非医疗」红线冲突，且会稀释小织单一形象。

### A5. Cult UI 的 reduced-motion 工程实践（A 类）

- **抄什么**：其 AI Blob Warp 组件「离屏即暂停动画、用户偏好减少动态即静止」的做法——把动画当「可关闭的资源」而不是装饰常量。
- **落在哪**：学生端全局动画规范（`ui-guidelines.md` 增补一节）：H5 用 `prefers-reduced-motion` 媒体查询 + IntersectionObserver 离屏暂停；小程序默认低动效基线，设置页提供「减少动态效果」手动开关（存本地）。
- **成本**：规范 0.5 天；开关与全局适配 1–2 天。
- **风险**：开关只增不删动效入口，避免做成「省电模式」式复杂设置。

### A6. moodiary / 心情 App 的记录范式（A 类）

- **抄什么**：①记录一步完成（选情绪 → 可选一句话 → 完成），不做表单页；②心情日历用色块而非分数；③不做 streak 火焰、不做「连续 N 天」倒计时压力（我们已有「连续创作记录」，保持陈述式不排名）；④写完后不给评价，只给一句承接（≤12 字，沿用 R 批空态文案语气）。
- **落在哪**：`pages/emotion` 便签与历史页、`pages/journey` 成长旅程页。
- **成本**：1–2 天（主要是交互简化与文案）。
- **风险**：既有功能只增不减，简化的是路径不是能力；历史数据展示不动。

### B1. Magic UI 精选纯 CSS 效果（B 类，挑 3 个）

- **shimmer 骨架条**（双端可用）：`linear-gradient` 背景 + `background-position` 关键帧动画，替换所有裸 spinner 之外的加载态；小程序支持。
- **柔光渐变描边**（H5 增强 / 小程序降级）：H5 用双层背景 `padding-box`/`border-box` 实现 1px 渐变描边；小程序不支持复杂背景裁剪时用纯色细边框（`#5BC4A8` 20% 透明度）降级。
- **点阵/细噪点背景**（H5 用 CSS radial-gradient 平铺；小程序建议省略）：只用于首页顶部氛围区，不用位图、不进主包。
- **落在哪**：首页三行动卡、作品卡、加载骨架（对接 V 批 StateBlock 体系）。
- **成本**：每个效果 0.5 天内；总量控制在 3 个以内，防止「质感通货膨胀」。
- **风险**：`conic-gradient` 类光环（Magic UI 的 border beam）小程序不可用，直接放弃该效果；所有新增动效必须过 §A5 的低刺激闸门。

### B2. View Transitions 页面过渡（B 类，仅 H5 渐进增强）

- **抄什么**：路由切换时的 150–200ms 交叉淡化，替代硬切。
- **落在哪**：H5 端 router 封装层一处；`document.startViewTransition` 存在才启用，否则静默跳过；小程序无 DOM 不涉及。
- **成本**：0.5–1 天。
- **风险**：仅做淡出淡入，不做共享元素飞入（大位移动效违反低刺激原则）；Firefox 144 才支持，必须有不存在 API 的静默降级。

### B3. Streamdown/remend 的流式 Markdown 修复思路（B 类，借思路）

- **抄什么**：流式期间对「未闭合的代码块/加粗/链接」做临时补全或延迟渲染，避免渲染闪烁。
- **落在哪**：`pages/ai` 消息渲染层。
- **怎么做**：我们 SSE 已是句级安全缓冲（A 方案），半截语法概率低；只需在渲染前加一个轻量校验：检测到未闭合 ``` 代码围栏时，本轮按纯文本渲染、闭合后再格式刷回。
- **成本**：0.5–1 天，不引 remend 库（TS 库，引库需拍板且收益有限）。
- **风险**：补全逻辑只在流式进行中生效，不得改写落库文本。

### B4. Motion 的动效参数哲学（B 类，只抄参数表）

- **抄什么**：微交互时长 150–300ms、错峰延迟 30–80ms、缓出用 ease-out 系；弹簧感仅在「小织」等吉祥物上使用（cubic-bezier(0.34, 1.4, 0.64, 1) 级别的轻回弹），其他界面元素不用弹簧。
- **落在哪**：令牌层新增 `--zy-duration-*` / `--zy-ease-*`（与管理端 animations.scss 的 zy-slide-up/zy-stagger 对齐），新动效一律引用令牌。
- **成本**：随 A2 一起做，增量半天。
- **风险**：不引 motion-v/@vueuse/motion（均依赖 DOM，小程序不可用；引库违反纪律，见 §7 拍板点）。

### B5. 呼吸引导圆（B 类）

- **抄什么**：4-7-8 / 箱式呼吸的视觉引导：一个圆形随阶段缩放（吸气放大 4s、屏息停留、呼气缩小 8s），配阶段文字与「随时可停」退出位。
- **落在哪**：`pages/mindfulness` 心念练习页。
- **怎么做**：CSS transform scale + 关键帧，无 JS 动画驱动；小程序用同一套 transform 动画（WXSS 支持）；尊重低动效开关——开启时改为静态圆 + 阶段文字倒计时。
- **成本**：1 天。
- **风险**：不得加声音自动播放、不得做「坚持打卡」话术；退出必须一键可达。

### C 类说明

没有整库可直接搬（候选全部是 React/Tailwind/DOM 依赖）。片段级可直接用的只有两段标准 CSS（reduced-motion 兜底块、shimmer 骨架），已并入 B1/B4，不单独列项。

## 6. 明确不建议借鉴的

1. **Aceternity 式高频炫技**（3D 卡片翻转、粒子流、无限滚动视差、光束扫过）：对情绪敏感用户是感官过载源，且全部依赖 React/Framer Motion/复杂滤镜，小程序双端不可行——风格与「温和度硬要求」直接冲突。
2. **滚动劫持与强制线性叙事**（landing 页常见的整屏滚锁）：剥夺用户控制感，焦虑场景下是大忌；我们的页面信息架构保持自由滚动。
3. **成就/竞技化激励**（streak 火焰、排行榜、XP、连续打卡倒计时压力）：对抑郁/焦虑倾向用户会形成「失败惩罚感」，与「不催促不评判」文案体系冲突；既有「连续创作记录」维持陈述式呈现，不升级为竞赛形态。
4. **拟人化过度承诺的 AI 陪伴范式**（「我永远陪着你」「我懂你的痛苦」类文案、诱导情感依赖的每日主动挽留）：违反「陪伴引导、非医疗/诊断」产品红线，高危情绪必须走既有危机干预链路，小织话术不做人格化加码。
5. **Flowise/Dify 式重型工作流可视化引入学生端**：管理端已有 `ai_trace` 与 AI 治理页覆盖观测需求；学生端暴露「链路/节点」只会制造困惑。另附警示：Flowise 已于 2026-07-29 官方宣布停止新功能开发，高 star 不代表可跟。

## 7. 需要拍板再落地的点

1. **是否破格引入 motion-v（H5 限定）**：学生端「不引入新库」纪律下，建议不破格——所需动效用 CSS 关键帧 + 令牌即可覆盖；若未来出现复杂编排需求再评估。
2. **令牌单源改造的排期方式**：建议随页面改造渐进迁移（不一次性全量替换），是否接受「双轨并存期」。
3. **对话页「正在做的事」状态条是否需要后端扩展 SSE 事件**：当前可先用既有 chunk/done + capability 字段做静态文案；若要显示真实检索/工具进度，需后端排期扩展事件类型。
4. **小程序是否评估 Skyline**：可解锁部分 CSS 能力（含受限的 backdrop-filter），但有已知渲染坑与全量回归成本；建议本期不评估。
5. **心情日历的视觉密度**：色块日历（moodiary 范式）与现有情绪历史页图表并存还是替换，需要产品定夺。

## 8. 来源（快照日期均为 2026-11-05 检索；数值以链接页面为准）

| 项目 | 链接 | star / 活跃度依据 |
| --- | --- | --- |
| shadcn/ui | <https://github.com/shadcn-ui/ui> | ≈127K：[starstruk 快照](https://starstruk.ai/repository/shadcn-ui/ui)（127.2K，3 日 +170）、[open-design 快照 2026-09-14](https://open-design.ai/skills/shadcn/)（123,708）、[scratchdb](https://scratchdb.com/tools/?oss=1&pricing=open-source)（127K，「last release 3 days ago」） |
| Tailwind CSS | <https://github.com/tailwindlabs/tailwindcss> | ≈98K：[gstars 快照 2026-10-28](https://www.gstars.dev/repo/tailwindlabs/tailwindcss)（98,125）、[devtoolsvs](https://devtoolsvs.com/bootstrap-vs-tailwindcss/)（98.0K）；[Tailwind 加入 Shopify 公告](https://tailwindcss.com/blog/tailwind-is-joining-shopify) |
| tweakcn | <https://github.com/jnsahaj/tweakcn> | 10,493，pushed 2026-09-03（GitHub API 快照，本次直接读取） |
| Motion | <https://github.com/motiondivision/motion> | 34,816，pushed 2026-11-02（GitHub API 快照，本次直接读取） |
| Motion for Vue | <https://github.com/motiondivision/motion-vue> / [npm motion-v](https://www.npmjs.com/package/motion-v) | star 未核实；npm 2.10.0 MIT，检索前一日发布 |
| @vueuse/motion | <https://github.com/vueuse/motion> / [官网](https://motion.vueuse.org/) | star 未核实 |
| Magic UI | <https://github.com/magicuidesign/magicui> | 22,688，pushed 2026-10-11（GitHub API 快照，本次直接读取） |
| Aceternity UI | <https://ui.aceternity.com/> | 非开源组件库（站点 registry + shadcn CLI 分发，其 API 页显示 lastUpdated 2026-11-03，技术栈 React 18+/Next 13+/Tailwind v4/Framer Motion） |
| Cult UI | <https://github.com/nolly-studio/cult-ui> / [官网](https://www.cult-ui.com/) | ≈6.8K：[ross.abutalabs 观测 2026-10-27](https://ross.abutalabs.com/products/cult-ui)（6,758）；官网组件 2026-10 有更新 |
| Vercel AI SDK | <https://github.com/vercel/ai> | ≈27.7K：[ai-sdk.dev 官网](https://ai-sdk.dev/)（27.7K）、[aibars 快照 2026-11-02](https://aibars.net/en/projects/873717646334627840)（27,681） |
| assistant-ui | <https://github.com/assistant-ui/assistant-ui>（原 Yonom/assistant-ui） | ≈12.9K：[官网 traction 实时页](https://www.assistant-ui.com/traction)（12.9K，npm 周下载 2.8M）、[ghtrends 2026-10-29](https://ghtrends.dev/assistant-ui/assistant-ui/)（12,812） |
| CopilotKit | <https://github.com/CopilotKit/CopilotKit> | ≈38K：[agentindex 2026-10-27](https://agentindex.app/en/tool/copilotkit/)（38.3k，30 日 +1.5k）、[starstruk](https://starstruk.ai/repository/CopilotKit/CopilotKit)（38.4K） |
| Lobe Chat | <https://github.com/lobehub/lobe-chat> | ≈70K：[lobehub 官方数据页](https://lobehub.com/es/skills/enuno-claude-command-and-control-lobe-chat)（70.1k+）；[v2.3.0 发布新闻 2026-11-01](https://olud.ai/news/2026-11-01.html)；许可证变更见 [kunavo 报道](https://kunavo.com/es/guides/lobechat-vs-open-webui) |
| Lobe UI | <https://github.com/lobehub/lobe-ui> / [npm @lobehub/ui](https://www.npmjs.com/package/@lobehub/ui) | ≈1.4K（[guthub 镜像](https://guthub.com/lobehub)，快照日期不完整，标未核实）；npm v6.15.2，检索前 5 小时有发布 |
| openui | <https://github.com/wandb/openui> | ≈22.5K：[dev.co](https://dev.co/ai/frameworks/wandb-openui)（22.5k）、[ai-tldr](https://ai-tldr.dev/tools/openui-wandb/)（22.7k）；活跃度未核实 |
| Flowise | <https://github.com/FlowiseAI/Flowise> | ≈55.5K：[gstars 2026-10-15](https://www.gstars.dev/repo/FlowiseAI/Flowise)；**停更公告 [flowiseai.com/sunset](https://flowiseai.com/sunset)（2026-07-29 code freeze）** |
| Dify | <https://github.com/langgenius/dify> | ≈158–160K：[gstars 2026-10-30](https://www.gstars.dev/repo/langgenius/dify)（159,438）、[tomrochette 引用 API 快照 2026-09-29](https://tomrochette.com/agents/orchestration/dify/)（157,458，pushed 2026-09-29） |
| Streamdown / remend | [streamdown.ai](https://streamdown.ai/docs/usage) / [Vercel changelog](https://vercel.com/changelog/new-npm-package-for-automatic-recovery-of-broken-streaming-markdown) / [v2.5 changelog 2026-03](https://aming.ourdisc.net/changelog/streamdown-2-5) | star 未核实；2026-03 有 v2.5 发布 |
| moodiary | <https://github.com/ZhuJHua/moodiary> | 2.1K：[HelloGitHub 快照](https://hellogithub.com/repository/6b4ef2844fa44f9e8fb03424e500b09e)（AGPL-3.0，活跃） |
| breathly | <https://github.com/mmazzarolo/breathly-app> / [Product Hunt](https://www.producthunt.com/products/breathly-just-breathe/launches/breathly) | star 未核实；开源呼吸训练 App（React Native，2021 年发布） |
| View Transitions | [MDN View Transition API](https://developer.mozilla.org/en-US/docs/Web/API/View_Transition_API) / [WebKit Interop 2026](https://webkit.org/blog/17818/announcing-interop-2026/) | Chrome 111+/Safari 18+/Firefox 144+；Interop 2026 继续推进跨文档过渡 |
| 小程序 backdrop-filter | [微信官方 Skyline WXSS 文档](https://developers.weixin.qq.com/miniprogram/dev/framework/runtime/skyline/wxss.html)、[WebView 不支持实测（CSDN）](https://blog.csdn.net/FungLeo/article/details/163420963)、[iOS Skyline 冲突案例](https://developers.weixin.qq.com/community/develop/doc/00048019ce054082e794976ca6bc00) | 见 §4 |
| weapp-tailwindcss | [官网](https://weapp-tw.icebreaker.top/docs/multi-platform) / [npm](https://www.npmjs.com/package/weapp-tailwindcss) | Tailwind 进小程序需此适配层，本期不引入 |
