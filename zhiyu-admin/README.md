# zhiyu-admin — 织愈管理端

Vue3 + Vite + Element Plus 的管理端（内容审核 / 关怀提醒 / 数据看板 / 传承人管理 / 用户管理），
与 `zhiyu-app/`（学生端）、`zhiyu-backend/`（后端）共用同一套 JWT 鉴权。

> 当前状态：T0.6a 脚手架 + 登录闭环。业务页面均为占位空壳，M2 里程碑接入。

## 开发

```bash
npm install
npm run dev      # http://localhost:5174
npm run build    # 产出 dist/
npm run preview  # 预览 dist/
```

- 开发端口固定 **5174**（`strictPort`）；学生端 H5 占 **5173**。
- 开发环境 `/api` 由 Vite 代理到 `http://localhost:8080`，生产环境由 Nginx 反代。
- 登录走 `POST /api/auth/login`；之后**每次进入受保护路由**都调 `GET /api/auth/me` 校验角色（严格模式）。
  角色 ∈ {1 传承人, 2 咨询师, 3 管理员} 放行；`role=0`（学生）提示「请使用学生端」并拒绝登录。
  该角色闸门只是路由跳转判断，真正的权限由后端 `/api/admin/**` 的 Spring Security 兜底。

## 目录

```
src/
├── main.js            # createApp + Pinia + Router + Element Plus（zh-cn）
├── App.vue            # <router-view />
├── router/index.js    # 路由表 + 严格模式守卫
├── stores/user.js     # token / userInfo / roleLabel
├── utils/request.js   # axios 封装（注入 Bearer、统一剥壳、401/403 处理）
├── views/             # Login.vue / Dashboard.vue
└── styles/            # variables.scss（设计变量）+ index.scss（全局样式与主题覆盖）
```

## 约定

- 色板与度量见仓库根目录 `docs/ui-guidelines.md`，不要另起一套颜色。
- 接口契约以 `openapi/openapi.yaml` 为准：**先改 YAML，再改代码**。
- 测试账号（见 `sql/init-data.sql`）：
  `admin / admin123`（管理员）、`counselor01 / couns123`（咨询师）、
  `inheritor01 / inherit123`（传承人）、`student01 / stu12345`（学生，仅学生端可用）。
