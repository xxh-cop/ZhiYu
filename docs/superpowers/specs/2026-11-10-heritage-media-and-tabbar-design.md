# 纹样馆实物质感与底部导航重设计（2026-11-10）

状态：方向已获批准（图片走 C 混合；导航走缺口式凸起）。待评审后进入实施。
上游：`docs/superpowers/specs/2026-11-10-heritage-atmosphere-student-design.md`、
`docs/design-tokens-20261102.md`、`AGENTS.md`（素材授权与 WXSS 门禁）。

## 1. 图片：C 混合路线

原则：**不抓未授权图**。目录里 `origin=ORIGINAL_STUDY`，来源 `mediaReuse=NOT_GRANTED`；
任何实物图不得冒充"原创教学构图"，必须带作者/来源/许可证。

- **主图（B 部分，AI 原创材质摄影）**：ImageGen 生成四族材质微距各 2 张
  （夏布经纬、竹编断面、剪纸负空间、瓷面开片），1600×1200，标注"材质示意 · 非实物文物"。
  用途：`HeritageCard` 封面底层纹理 + 详情页大图背景。
- **纹样层（保留）**：`PatternLive`/`buildPatternSvg` 几何绘制叠在材质底之上，
  形成"真实材质 + 原创几何"的合成，不改变 `origin` 语义。
- **实物参考（A 部分）**：仅收 Wikimedia Commons / Unsplash / Pexels 中可核实许可者，
  登记到 `docs/heritage-media.json`（id/sourceUrl/author/license/label），详情页新增
  「实物参考」横滑小图廊，逐张显示署名与许可；无可用图则整块不渲染。
  本机外网 curl 被沙箱阻断，A 部分图片由用户提供 URL 或本地文件后再登记；未登记不入选。
- **降级链**：材质图加载失败 → 纯色 tint；`PatternLive` 失败 → manifest PNG；都失败 → 首字占位。

## 2. 底部导航：缺口式凸起（custom tabBar）

### 视觉与动效
- 导航条高 112rpx + 底部安全区；底色 `--zy-card-bg`，顶部 1rpx `--zy-card-line`；
  中央向下切 88rpx 宽、44rpx 深的缺口（SVG data-URI 背景，WXSS 兼容）。
- 中央圆钮 112rpx，渐变 `--zy-brand-600 → --zy-brand-500`，白色加号 40rpx，
  上移 28rpx，投影 `0 8rpx 24rpx rgba(33,126,118,.35)`；按下 `scale(.92)`（150ms）。
- 普通 tab：图标 40rpx + 文字 `--zy-text-micro`；选中态图标与文字 `--zy-brand-700`，
  下方 48rpx×4rpx 胶囊指示条以 200ms `--zy-ease-smooth` 位移到当前项。
- 中间项进入后加号旋转 45°（200ms）表达"可收回/关闭"语义；不做循环动画。
- `prefers-reduced-motion`：全部 transition/animation 关闭，指示条直接跳位。
- 无障碍：`role=tablist`，每项 `role=tab` + `aria-selected`，中央钮 `aria-label="开始"`；
  触控目标 ≥88rpx。

### 跨端实现（关键代价）
- `pages.json`：`tabBar.custom = true`，保留 5 项 `list`（switchTab 路由仍依赖）。
- 小程序：新增 `src/custom-tab-bar/index.vue`（uni-app 约定的自定义 tabBar 挂载点）。
- H5：uni-app 不自动挂载 custom-tab-bar，抽共享展示组件 `src/components/ZyTabBar.vue`，
  由 `#ifdef H5` 在 5 个 tab 页根节点挂载；小程序侧 `custom-tab-bar/index.vue` 复用同一组件。
- 切换统一 `uni.switchTab`；选中态用 `getCurrentPages()` 末页路由推导，避免双份状态。
- 回滚方案：`custom` 改回 false 即恢复原生 tabBar（保留在规格里，便于演示前保底）。

### 涉及页面
首页、社区列表、开始、小组列表、我的，共 5 个 tab 页；`pages.json` tabBar 段；
新增 `ZyTabBar.vue` 与 `custom-tab-bar/index.vue`；`theme.scss` 增 tabBar 令牌（不改色值，仅尺寸/层级）。

## 3. 验收
- `check:wxss`/`isclass`/`elements`/`contrast` + H5 与微信小程序双构建。
- 小程序开发者工具确认：缺口不裁切、安全区、5 页切换选中态、中央钮凸起与旋转、
  原生 tabBar 不再出现。
- H5：5 页切换、深链直达 tab 页选中态正确、无横向溢出、reduced-motion 静态。
- 图片：双主题截图；断网/失败图走降级链；实物图廊无图时不渲染；署名可见。
- 素材：新增文件登记作者/来源/许可；AI 图标注"材质示意"。

## 4. 风险
- 自定义 tabBar 失去原生切换性能与内置状态，需自维护安全区与选中态；回滚开关已定。
- 小程序 `image` 对 SVG 支持不稳：材质与印章优先 PNG/JPEG；SVG 仅 H5 与降级。
- 实物图版权：未核实许可一律不入库；不得用抓取图替代。
