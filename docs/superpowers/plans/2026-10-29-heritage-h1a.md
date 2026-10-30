# 非遗 H1a：只读目录 API 与学生纹样馆 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 在保留原首页和原功能的前提下，新增一个登录后可读的非遗目录 API，以及纹样馆/详情页和首页单入口。

**Architecture:** 后端新增 `modules/heritage`，从已核实的 `catalog/heritage-catalog.json` UTF-8 只读加载，不建表、不写库；返回 typed record。学生端新增两页并复用现有 `growth.css`/主题；首页只在现有 DISCOVER 区域加一个入口，不重排。

**Tech Stack:** Java 17 / Spring Boot 3.4 / Jackson / JUnit + MockMvc；UniApp Vue 3 / Wot 1.14 / 现有 request；不新增依赖。

**Spec:** `docs/superpowers/specs/2026-10-29-heritage-student-experience-design.md`

## Global Constraints

- 原首页布局、区块顺序、全部原入口和原功能保持不变；只新增一个“非遗纹样馆”入口。
- 不改聊天、推荐、创作、发布、社区、小组、成长、正念、活动、隐私等既有页面行为。
- 所有纹样为原创教学再设计，不是传统原作；来源只作材料/技艺背景。
- 不新增错误码；不建表、不改 schema/H2/兼容检查；不提交/推送/切分支。
- 目录 ID 为字符串；详情来源只使用已登记官方链接；不复制外部图片/文字。
- 首页入口不遮挡原内容；不新增第二套 UI 库；保留小织与 logo。

## Task 1：后端只读目录服务与 API

**Files:**
- Create: `zhiyu-backend/src/main/java/com/zhiyu/modules/heritage/model/HeritageCatalog.java`
- Create: `zhiyu-backend/src/main/java/com/zhiyu/modules/heritage/service/HeritageCatalogService.java`
- Create: `zhiyu-backend/src/main/java/com/zhiyu/modules/heritage/controller/HeritageController.java`
- Create: `zhiyu-backend/src/test/java/com/zhiyu/modules/heritage/HeritageCatalogServiceTest.java`
- Create: `zhiyu-backend/src/test/java/com/zhiyu/modules/heritage/HeritageControllerTest.java`
- Modify: `openapi/openapi.yaml`（`/api/heritage/catalog`）
- Create: `features/21-heritage-catalog.feature`

**Interfaces:**
- `HeritageCatalogService.catalog()` returns `HeritageCatalog`; requires `UserContext.getUserId()!=null`, otherwise `BusinessException(1002,"请先登录")`.
- `GET /api/heritage/catalog` returns `Result<HeritageCatalog>`.

**Record shape (exact fields in catalog JSON):**

```java
public record HeritageCatalog(String version, String disclaimer,
  List<Source> sources, List<Tradition> traditions, List<Pattern> patterns) {
  public record Source(String id, String title, String organization, String url,
    String accessedAt, String mediaReuse, List<String> facts) {}
  public record Tradition(String id, String name, String shortName, String region,
    String category, String registryCode, Integer listedYear, List<String> sourceIds,
    String summary, List<String> materials, List<String> process,
    List<String> safety, String practiceBoundary) {}
  public record Pattern(String id, String traditionId, String name, String family,
    String variant, Integer detail, List<String> palette, String origin,
    List<String> sourceIds, String designNote, String learningFocus,
    Integer minutes, String level) {}
}
```

- [ ] **Step 1：写服务失败测试（先 RED）**

```java
@Test void catalogLoadsFourTraditionsAndTwentyFourPatterns() throws Exception {
  var service = new HeritageCatalogService(new ObjectMapper());
  UserContext.set(new LoginUser(1L, 0));
  try {
    var data = service.catalog();
    assertEquals(4, data.sources().size());
    assertEquals(4, data.traditions().size());
    assertEquals(24, data.patterns().size());
    assertEquals(24, data.patterns().stream().map(HeritageCatalog.Pattern::id).distinct().count());
    assertTrue(data.patterns().stream().allMatch(p -> "ORIGINAL_STUDY".equals(p.origin())));
    assertTrue(data.patterns().stream().allMatch(p -> data.traditions().stream()
      .anyMatch(t -> t.id().equals(p.traditionId()))));
  } finally { UserContext.clear(); }
}
```

- [ ] **Step 2：运行测试确认因类不存在失败**

`cd zhiyu-backend && mvn -q -Dtest=HeritageCatalogServiceTest test "-Dmaven.repo.local=../.ai-tmp/m2"`

- [ ] **Step 3：实现 record 与服务**

```java
@Service
public class HeritageCatalogService {
  private final HeritageCatalog catalog;
  public HeritageCatalogService(ObjectMapper json) throws IOException {
    try (var reader = new InputStreamReader(
        new ClassPathResource("catalog/heritage-catalog.json").getInputStream(),
        StandardCharsets.UTF_8)) {
      this.catalog = json.readValue(reader, HeritageCatalog.class);
    }
  }
  public HeritageCatalog catalog() {
    if (UserContext.getUserId() == null) throw new BusinessException(1002, "请先登录");
    return catalog;
  }
}
```

启动时校验四个来源/四门工艺/24 纹样、ID 唯一、来源引用闭合、palette 四色、family/variant 组合在允许集内；失败抛 `IllegalStateException`。不连数据库。

- [ ] **Step 4：实现控制器**

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/heritage")
public class HeritageController {
  private final HeritageCatalogService service;
  @GetMapping("/catalog")
  public Result<HeritageCatalog> catalog() { return Result.success(service.catalog()); }
}
```

- [ ] **Step 5：控制器测试**

用 `MockMvcBuilders.standaloneSetup(new HeritageController(service)).build()`，`UserContext.set(new LoginUser(1L,0))`；断言 HTTP 200、`$.code=0`、`$.data.traditions.length()=4`、`$.data.patterns[0].id` 为字符串；测试后清理 UserContext。另测未登录时 service 抛 1002（不依赖 Spring Security）。

- [ ] **Step 6：OpenAPI + Gherkin**

OpenAPI 在 `/api/craft/starter` 附近新增 `/api/heritage/catalog`，tags `[Heritage]`，operationId `heritageCatalogGet`，security bearerAuth，200/401/403 描述；说明 data 包含 version/disclaimer/sources/traditions/patterns，ID 字符串、原创教学、mediaReuse=NOT_GRANTED 的含义。

Gherkin `features/21-heritage-catalog.feature` 覆盖：登录学生读到 4/4/24、引用闭合、原创标识；未登录 401/1002；无诊断措辞。

- [ ] **Step 7：定向测试与编译**

`cd zhiyu-backend && mvn -q -Dtest=HeritageCatalogServiceTest,HeritageControllerTest test "-Dmaven.repo.local=../.ai-tmp/m2"`

- [ ] **Step 8：报告**

写 `.ai-tmp/h1a-backend-report.txt`：状态、文件、RED/GREEN、测试数、未提交、未建表。

## Task 2：学生纹样馆与详情页

**Files:**
- Create: `zhiyu-app/src/pages/heritage/gallery.vue`
- Create: `zhiyu-app/src/pages/heritage/detail.vue`
- Modify: `zhiyu-app/src/pages.json`（新增两页，标题“非遗纹样馆”“纹样详情”）
- Modify: `zhiyu-app/src/pages/home/index.vue`（只在 `side-column` 的 `daily-note` 后新增一个入口卡）
- Create: `zhiyu-app/tests/heritage-ui.test.mjs`（纯函数：筛选/搜索/来源映射/路由参数）

**Interfaces:**
- 两页通过 `request({url:'/api/heritage/catalog'})` 读取数据；不新增 API 客户端抽象。
- 详情页 `onLoad({id})` 按 `pattern.id` 查找；未知 id 显示“纹样不存在”+返回馆页。
- 首页入口 `goHeritage()` 只 `uni.navigateTo({url:'/pages/heritage/gallery'})`；不改变其他函数/布局。

**Gallery UI contract:**
- `growth-page` 壳；标题“非遗纹样馆”；说明“24 份原创教学构图 · 来源可查”。
- 原生 `input` + 可见 label“搜索纹样”；筛选按钮“全部/夏布绣/竹编/剪纸/制瓷”，`aria-pressed`；选中态可见。
- 卡片网格 2 列（手机）/3–4 列（H5 宽屏）；每卡显示名称、工艺短名/地域、难度、数字练习估时、`原创教学` 标签；点击进入详情。
- 加载：骨架或“正在整理纹样…”；错误：错误文案+“重试”；空：解释+“清空筛选”。
- 筛选/搜索只在前端过滤，不每次请求。
- 图片先使用 CSS/占位几何或本地静态图（Task 3 提供）；不得用外部图片。

**Detail UI contract:**
- 标题/工艺/地域/难度；大图；学习观察点；原创设计说明；材料与安全；数字练习/实物工艺边界；来源链接（`https`、`target=_blank`、`rel=noopener noreferrer`）。
- 明确“来源只作材料与技艺背景参考，不表示官方认可本图案”。
- 底部“去看看手作”返回 `pages/starter/index`；不新增假 AI/上传/保存。
- 错误/空/重试；返回按钮；免责声明“本系统不能替代专业心理咨询”。

- [ ] **Step 1：先写纯函数测试**
  - `filterHeritagePatterns(patterns, {keyword, traditionId})` 支持名称/地域/短名搜索，空条件返回全部，按 id 去重。
  - `traditionLabel`/`familyLabel` 映射；未知值回退原文。
  - 测试真实使用 24 条 catalog JSON，不 mock。
- [ ] **Step 2：RED 后实现页面与入口**
- [ ] **Step 3：保留原首页验证**
  - 对比 `home/index.vue` 原有 `mood-card`、`practice-grid`、`craft-row`、`explore-links` 等结构未变；只新增一个 `button.heritage-entry`。
  - 入口不改变 `side-column` 原有推荐/每日一句顺序。
- [ ] **Step 4：运行学生测试与构建**
  - `node --test zhiyu-app/tests/*.test.mjs`
  - `npm --prefix zhiyu-app run build:h5`
  - `npm --prefix zhiyu-app run build:mp-weixin`

## Task 3：本地原创缩略图

**Files:**
- Create: `zhiyu-app/src/static/heritage/<pattern-id>.png`（24 张，项目自有）
- Create: `.ai-tmp/generate-heritage-thumbs.cjs`（scratch 生成脚本）
- Optional: `docs/heritage-assets.md`（生成来源说明）

- [ ] 用 `zhiyu-app/src/utils/heritage-pattern.js` 的 `buildHeritageGeometry` 和 catalog palette 生成 512×512 PNG；背景 palette[0]，路径来自几何；不使用外部素材。
- [ ] 验证每张非空、文件名=pattern id、24 张；缩略图在 96px/160px 可辨。
- [ ] 页面使用 `/static/heritage/<id>.png`；详情大图同一资源。
- [ ] 构建 H5/小程序，确认图片路径可加载。

## Task 4：浏览器端到端验收

- [ ] 启动后端（使用现有本地配置；不读取/输出密钥；若不可用则记录未验收，不用 Mock 冒充）。
- [ ] 登录学生账号，从原首页点击新增入口 → 纹样馆 → 筛选/搜索/空态 → 详情 → 来源链接属性；确认原首页其他入口仍在且布局未变。
- [ ] 375/768/1280 无横向溢出；加载/错误/重试/空态；键盘焦点；控制台错误。
- [ ] 截图 output/playwright；不包含真实用户数据。
- [ ] 后端定向测试、编译；学生 H5/小程序构建；OpenAPI YAML 解析；Gherkin 可解析。
- [ ] 报告 `.ai-tmp/h1a-e2e-report.txt`；不提交/不推送。

## 明确不做（本批）

- 不改首页布局/开始页、不删任何原功能。
- 不做管理端 CRUD/数据库表/迁移/H2/兼容检查；内容来自已审核 catalog JSON。
- 不做 AI 共创、文化卡、RAG 图扩展、倾听小结/AI 治理。
- 不复制外部图片；不把原创教学称为传统原作；不宣称疗效。
