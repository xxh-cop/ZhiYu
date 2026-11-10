# 学生端「非遗气息深化」设计规格（2026-11-10）

状态：待用户评审。方案 C（双线并进）已获口头批准；文化卡允许导出分享图已获批准。
上游依据：`docs/ui-upgrade-student-spec-20261108.md`（暖纸青玉 P0–P5 与双主题）、
`docs/design-tokens-20261102.md`（令牌唯一权威）、
`docs/superpowers/specs/2026-10-29-heritage-student-experience-design.md`（非遗总纲）。

## 1. 诊断（为什么气息不够浓）

1. 核心非遗 surface 穿旧衣：`zhiyu-app/src/sub-heritage/pages/heritage/{gallery,detail,studio}.vue`
   仍依赖 `zhiyu-app/src/styles/growth.css`（第二代通用样式、旧描边兜底、裸字号），
   未接 P2 组件层与缝线 / 展品封面 / 引言语言。
2. 文化字段灰字堆叠：`heritage-catalog.json` 的 `registryCode / listedYear / region /
   process / quote / practiceBoundary` 在界面上无形状，名录身份与地域不可感知。
3. 纹样是静态 PNG：`sub-heritage/static/heritage/` 24 张图；确定性渲染器
   `zhiyu-app/src/utils/heritage-pattern.js` 只在配色台消费，缺工序演示。
4. 高频页通用白卡：recommend / inheritor / materials / work-publish / craft-session /
   practice-short / start 无纹样渲染；`design/logo/zhiyu-seal-student.svg` 闲置；
   纸纹仅在可选暖纸主题生效，默认云白为纯白。
5. 主线断点：文化卡仅复制文字；纹样详情不能发起绑 `heritagePatternId` 的跟做会话；
   地域探索（总纲 U2）未落地。

## 2. 目标与非目标

目标：让非遗气息从"点状"变"弥漫"，且每一处增强都有数据或来源支撑；三阶段各自可交付可验收。

非目标（红线，沿用用户既定约束）：
- 不做 3D / 粒子 / 玻璃拟态 / 科技或游戏感；不引在线字体；不新增吉祥物。
- 不动青玉色阶（主 `#217E76`）；`gold #D9A441` 仅图形；错误态用 care 绛 `#A64842`；禁纯红纯黑。
- 不编造寓意：节令 / 地域策展只用 catalog 事实字段（region / listedYear / learningFocus），
  无来源的节气寓意一律不做。
- 合成传承人故事继续标注"合成教学叙事"；免责声明全程可见；不做证书、不伪造资质。
- 不抢跑 P7 纯一致性债（px/rpx、emoji 图标、旧阴影五档删除）；本规格只做非遗 flavor 部分。
- 受控共创边界不变：白名单三变体、不写库、不下发可执行内容。

## 3. 视觉语言增量（共享层）

新增组件（`zhiyu-app/src/components/`，全部类选择器、WXSS 合规、双主题变量驱动）：
- `HeritageRegistryPlate.vue`：名录牌。衬线 registryCode + listedYear + category，
  缝线竖条分隔；数据来自 tradition；空字段整块不渲染（不显示"未知"）。
- `RegionSeal.vue`：地域印。复用 `design/logo/zhiyu-seal-student.svg` 形制，
  内文为 region 简称（新余 / 瑞昌 / 景德镇），gold 仅描边图形、文字用暖墨；
  提供 `size` 与 `tone`（heritage / bare）两个 prop。
- `ProcessStrip.vue`：工序三步微图。输入 `process[3]`，每步一个 CSS/SVG 微示意 + 短句；
  横向缝线连接；当前步高亮由 prop 控制（供跟做会话复用）。
- `PatternLive.vue`：活体纹样。消费 `heritage-pattern.js` 的归一化 layers，
  输出 SVG（H5 与 MP 均用 SVG 组件或 canvas 二选一，优先 SVG 以保 crisp）；
  prop：`patternId / palette / detail / animate`；`animate` 为"逐笔绘出"入场
  （stroke-dashoffset 或分层 opacity 阶梯），`prefers-reduced-motion` 时直接静态。
- `HeritageCard.vue`：通用卡替换件。纹样角饰（内联 SVG data-URI）+ 缝线底边 +
  品类材质底纹（weave 经纬 / stitch 针脚 / cutout 负空间 / porcelain 瓷白，纯 CSS）；
  slot：cover / title / meta / action；无 cover 时按 category 回落到材质底纹 + 首字。
- `CultureCardPoster.vue`（阶段三）：文化卡分享图渲染器，canvas 绘制，见 §6。

令牌增量：不新增色值。仅在 `theme.scss` 增加**强度变量**：
`--zy-paper-noise-alpha`（云白默认 0.012、暖纸维持现值），使默认主题也有极淡纸纹；
`--zy-pattern-ink`（= 现暖墨）供活体纹样默认墨色。所有新组件只引用既有 `--zy-*`。

## 4. 阶段一 · 核心三页活体化

改动：`sub-heritage/pages/heritage/{gallery,detail,studio}.vue` 脱离 `growth.css`，
改用 `zy-toolkit.scss` + P2 组件 + 上述新组件；`studio.vue` 保留四步状态机与受控共创，仅换承载样式。

- gallery：响应卡网格改 `HeritageCard` + `PatternLive`（静态缩略，animate=false）；
  筛选与搜索行为不变；每卡加 `RegionSeal` 小印与原创教学标签。
- detail：展品封面式大图改 `PatternLive`（animate=true）；新增 `HeritageRegistryPlate`、
  `ProcessStrip`（process 三步）、四色色板保留；`quote` 用引言语言（缝线竖条 + 金引号）；
  `practiceBoundary / safety / sources` 保持现有边界文案与三层来源标识。
- 工序演示：detail 内按 family 增加一个"看工序"切换，播放 `ProcessStrip` 的逐步高亮
  + `PatternLive` 分层 reveal（weave 经纬交替 / stitch 针脚走向 / cutout 负空间 / porcelain 分层）。
  文案绑定 `learningFocus` 与 `process`，不新增寓意文案。
- 数据流：仍只读 `GET /api/heritage/catalog`；渲染器输入不变（family/variant/detail/palette），
  不新增后端字段。静态 PNG 保留为降级路径（`PatternLive` 渲染异常或低端机配置下
  回落 manifest 对应 PNG）。
- 状态：加载骨架 / 空目录 / 接口失败重试 / 纹样不存在，沿用 StateBlock 语义。
- 验收：H5+MP 构建；`check:wxss*`、`check:contrast` 全过；双主题截图；
  24 纹样 `PatternLive` 输出与 `manifest.json` 几何一致性脚本比对；reduced-motion 截图。

## 5. 阶段二 · 高频页弥漫

- 用 `HeritageCard` 替换：`craft/recommend.vue`、`inheritor/list.vue`、`materials/index.vue`、
  `materials/kits.vue`、`work/publish.vue` 预览卡、`craft/session.vue` 步骤卡、
  `practice/short.vue` intro/done 卡、`start/index.vue` 入口卡。行为与接口不变。
- `inheritor/detail.vue` 头部加 `RegionSeal` + 名录牌（若该传承人有 registry 数据），
  社交主页风收敛为"匠人档案"风；无数据则不渲染印。
- 默认云白启用极淡纸纹（`--zy-paper-noise-alpha: 0.012`），暖纸不变；
  `prefers-reduced-transparency` 时两者都移除。
- 印章落款：`work/publish.vue` 成功层与 `work/detail.vue` 作者行加 `RegionSeal`/印章落款
  （学生端用 `zhiyu-seal-student.svg`），作为"手作完成"的仪式反馈，不做认证含义。
- 验收：逐页双主题截图 + 对比度闸口不回退 + 双端构建；与 P6/P7 划项表对照不重叠纯一致性债。

## 6. 阶段三 · 主线闭环与分享

- 跟做闭环：detail 增加「用这个纹样跟做」→ `POST /api/practice/sessions`
  携带 `heritagePatternId`（契约已有，字符串）→ 会话步骤取 `tradition.process`
  映射为三步引导（复用 `ProcessStrip` 当前步高亮）→ 完成写 artifact（文化卡）→ 成长旅程可见。
  不新增接口；若步骤映射需要后端字段，先改 `openapi/openapi.yaml` 再改代码。
- 地域探索：gallery 增加 region 筛选 chips（事实字段）+ 一个"地域"视图分组；
  不做节令寓意策展。入口在首页非遗入口卡副标题与 gallery 顶部。
- 文化卡分享图：`CultureCardPoster.vue` 以 canvas 绘制 1080×1440：
  活体纹样 + 四色色板 + 金句（quote 或用户备注）+ 名录牌 + 来源行 +
  "原创教学 · 屏幕练习不是实物工艺" + 免责声明 + 印章落款。
  H5：`canvas.toDataURL` → 下载/长按保存提示；MP：`canvasToTempFilePath` →
  `saveImageToPhotosAlbum`，拒绝授权时降级为"复制文字 + 站内保存"，不阻断主流程。
  边界：仅导出**用户自己的练习成果卡**；受控共创变体本身仍不导出、不证书（与总纲一致）。
- features：新增 `features/43-heritage-follow-along.feature`、
  `44-heritage-region-explore.feature`、`45-culture-card-share.feature`
  （各含 happy + 异常路径：授权拒绝 / 接口失败 / 纹样下架）。
- 验收：真机分享权限路径（允许/拒绝）、双端构建、契约解析、双主题截图、
  分享图含免责声明与来源的目视检查。

## 7. 门禁与验收总表

每阶段交付必须通过：`pnpm/build` H5 与 MP 双构建；`check:wxss`、`check:isclass`、
`check:elements`、`check:contrast`；双主题（云白/暖纸）关键页截图存 `output/playwright/`；
`git diff --check`；不提交 `.env`/密钥/截图以外的临时物。
阶段一额外：纹样几何一致性比对。阶段三额外：真机分享授权两路径 + features 场景。

## 8. 风险与降级

- MP canvas/SVG 性能：`PatternLive` 限制 MAX_POINTS（渲染器已有 100 上限），
  列表缩略 animate=false；失败回落静态 PNG。
- 分享图鉴权失败：降级复制文字 + 站内保存，主流程不阻断。
- 纸纹默认化可能影响对比度闸口：alpha 0.012 起步，闸口不过则回退 0（云白纯白）并记录。
- 与 P6/P7 并行冲突：阶段二只换承载组件与 flavor，不动 px/rpx 与 emoji 划项。
- 寓意红线：所有新增文案只能来自 catalog 字段或用户输入；评审时逐条核对来源。

## 9. 交付顺序与提交拆分建议

阶段一 → 阶段二 → 阶段三，各自一个语义提交组（`feat(app): 非遗气息深化·阶段N`），
契约/迁移/features 与代码同提交；每阶段结束跑门禁并给用户看截图后再进下一阶段。
本文档评审通过后由 writing-plans 产出实施计划；文档本身提交需用户另行确认（AGENTS.md）。
