# 织愈 · 品牌标志资产

重绘于 2026-10-26。识别来自品牌名本身：**「织」字方印 + 文楷字标 + 一条收在针点的金线**。
不使用心形、叶子、线团等通用图形符号；双端共用同一结构，只换配色。

## 资产清单

| 文件 | 用途 |
|---|---|
| `zhiyu-seal-student.svg` / `zhiyu-seal-admin.svg` | 字印正色（浅底、App 图标、侧栏） |
| `zhiyu-seal-inverse-student.svg` / `zhiyu-seal-inverse-admin.svg` | 字印反白（品牌色底、深色模式） |
| `zhiyu-logo-horizontal-student.svg` / `-admin.svg` | 横版组合：登录页、页头、文档 |
| `zhiyu-logo-splash-student.svg` / `-admin.svg` | 竖版含口号：启动页、空态、关于页 |
| `zhiyu-logo-splash-inverse-student.svg` | 薄荷青底启动页 |
| `favicon-student.ico` / `favicon-admin.ico` | 16 / 32 / 48 三档 |
| `png/` | 由 SVG 渲染的位图，供小程序等不支持外链字体的场景使用 |

## 使用规则

- 字印**不叠加金线**。金线只出现在字标，用于承载「缝补」的语义；字印保持干净才压得住小尺寸。
- 最小尺寸：字印 16px；横版组合宽 120px；低于此改用字印。
- 留白：四周不小于字印边长的 1/4。
- 反白：置于品牌色或深色底时，使用 `*-inverse-*`，不得用滤镜或半透明模拟。
- 禁止：拉伸变形、描边、投影、渐变填充、更换非令牌色、把字印拆成单字以外的笔画。
- 配色取自 `design/tokens.json`：学生端 `#2f7566` + `#bb914d`；管理端 `#4F46E5` + `#F59E0B`。

## 字体来源与授权

| 项 | 内容 |
|---|---|
| 字体 | 霞鹜文楷 LXGW WenKai，Regular |
| 上游 | https://github.com/lxgw/LxgwWenKai |
| 作者 | 霞鹜（lxgw） |
| 许可证 | SIL Open Font License 1.1 |
| 取得方式 | npm 包 `lxgw-wenkai-webfont@1.7.0` 的 unicode 子集 woff2（子集 108/112/113/114/116/118/119） |
| 本仓库的修改 | 仅提取「织愈」与口号「用一针一线，缝补青春的焦虑」共 14 个字形，转为 SVG 轮廓路径；金线、圆角方印与配色为项目原创绘制 |
| 是否分发字体 | 否。仓库内只有轮廓路径，不含字体文件，运行时不依赖字体安装 |

## 重新生成

```bash
# 1. 本地工具链（不污染项目依赖）
python -m pip install --target .ai-tmp/pylibs fonttools brotli
mkdir -p .ai-tmp/fonts/subsets
for n in 108 112 113 114 116 118 119; do
  curl -L -o .ai-tmp/fonts/subsets/subset-$n.woff2 \
    "https://cdn.jsdelivr.net/npm/lxgw-wenkai-webfont@1.7.0/files/lxgwwenkai-regular-subset-$n.woff2"
done

# 2. 生成 SVG（在仓库根目录执行）
PYTHONPATH=.ai-tmp/pylibs python design/logo/generate_logo_svgs.py

# 3. 导出 PNG 与 favicon
npm install --no-save --prefix .ai-tmp/resvg @resvg/resvg-js
NODE_PATH=.ai-tmp/resvg/node_modules node design/logo/render_logo_pngs.js
```

`generate_logo_svgs.py` 从 `.ai-tmp/fonts/subsets/` 读取子集，用 fontTools 的 `SVGPathPen` 转曲。
字标按 **em 字号**排版，不按字形包围盒高度——否则「一」「，」这类扁字会被放大成横条。

## 概念稿

方向探索过程（参考结论、被否掉的图形语言）见 UI 基线设计讨论，概念板为会话内临时产物，不入仓库。
