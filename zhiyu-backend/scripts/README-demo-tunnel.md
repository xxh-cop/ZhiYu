# 本机演示启动链（后端 + 公网 HTTPS 隧道）

本目录下的 `start-demo-tunnel.ps1` 用于把**本机运行的织愈后端**临时暴露成 HTTPS 公网地址，
供已部署的学生端 / 管理端站点调用。

它是一条**额外**的演示辅助脚本：不修改 `.env`、不修改 `application*.yml`、
不改变任何现有启动方式。

## 前提

| 依赖 | 要求 |
|---|---|
| 操作系统 | Windows（PowerShell 5.1 及以上） |
| Java | JDK 17；`JAVA_HOME` 或 PATH 能取到 `java.exe`，也可用 `-JavaExe` 指定 |
| MySQL | 本机 3306 正在监听（脚本只检测，不负责启动） |
| Redis | 本机 6379 正在监听（脚本只检测，不负责启动） |
| 后端产物 | 已执行 `mvn package -DskipTests`，`zhiyu-backend/target` 下有可运行 jar |
| 出网 | 能 SSH 连接隧道服务（默认 `serveo.net` 的 22 端口；备选 `nokey@localhost.run`） |

后端启动时会自行读取仓库根目录已有的 `.env`（应用内置的 dotenv 加载），
脚本**不读取、不修改、不覆盖** `.env`。

## 一条命令

在仓库根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\zhiyu-backend\scripts\start-demo-tunnel.ps1
```

脚本会依次：注入跨域白名单 → 启动后端（8080 空闲时）→ 建立 SSH 隧道 → 轮询隧道日志拿到
`https://...` 地址 → 用该地址真实请求一次 `/api/crisis/hotlines` 验证 → 打印结果。
成功时退出码 0，失败退出码 1。

## 可覆盖的参数（环境变量或同名参数）

| 参数 | 环境变量 | 默认值 | 说明 |
|---|---|---|---|
| `-CorsAllowedOrigins` | `ZHIYU_CORS_ALLOWED_ORIGINS` | 两个已部署站点 + `http://localhost:*` + `http://127.0.0.1:*` | 逗号分隔，支持通配模式 |
| `-Port` | `ZHIYU_DEMO_PORT` | `8080` | 本机后端端口；脚本启动后端时同步传 `--server.port` |
| `-RemotePort` | `ZHIYU_TUNNEL_REMOTE_PORT` | `80` | 隧道远端端口（localhost.run / serveo 用 80） |
| `-SshTarget` | `ZHIYU_TUNNEL_SSH_TARGET` | `serveo.net` | 隧道服务 SSH 目标，备选 `nokey@localhost.run` |
| `-JavaExe` | `ZHIYU_JAVA_EXE` | 自动解析 | `java.exe` 绝对路径 |
| `-JarPath` | `ZHIYU_DEMO_JAR` | `target/zhiyu-backend-0.1.0-SNAPSHOT.jar` | 未命中时回退到 `target` 下最新的 jar |

运行时状态、日志、known_hosts 都写在仓库根目录的 `.ai-tmp/`（已被 `.gitignore` 忽略）：

- `.ai-tmp/demo-backend-<端口>.log`
- `.ai-tmp/demo-tunnel-<端口>.log`
- `.ai-tmp/demo-tunnel-<端口>.json`（记录 URL / PID，用于防止重复起隧道）

## 零影响约定（重要）

- 8080 已被占用时：脚本只提示占用 PID，**跳过启动后端**，不抢端口、不结束任何进程；
  随后继续建隧道。若占用者不是织愈后端（健康检查不通过），脚本直接报错停止，不做任何处理。
  若占用者是织愈后端但启动时没带跨域白名单，两个站点仍会跨域失败。
- 同一后端端口已有隧道在跑时（不区分隧道服务商）：脚本**不会**再起第二条，直接打印已有记录并退出。
- 不修改 `.env`、`application*.yml`、`README`、现有启动脚本，也不改动工作区里其他未提交内容。
- 不负责启动 / 重启 MySQL、Redis，也不负责 `mvn package`。

## 它解决不了什么

- **隧道域名每次重连都会变**（localhost.run 免费域名还会周期性轮换）。
  脚本只负责打印本次拿到的域名，**不会**自动修改前端、不会自动重新构建；
  两个站点必须把 `VITE_API_BASE_URL` 改成新域名后**重新构建部署**，否则仍会登录失败。
- 脚本不保证隧道长期可用：SSH 断开或服务端限流后需要重新运行脚本，
  新域名同样要回填到前端。
  - 实测 2026-11-23：`localhost.run` 会返回 `tunnel activity timeout`（隧道进程还在但公网 502），
    此时改用 `-SshTarget serveo.net` 可恢复；serveo 也可能在数分钟后被对端关闭，届时重跑脚本。
  - 免费隧道都不适合长期演示，正式场合请换固定域名方案。
- 不是生产部署方案：没有固定域名、没有 SLA、没有 ICP/HTTPS 证书管理。

## 关闭

先看状态文件里的 PID，然后：

```powershell
$state = Get-Content .\.ai-tmp\demo-tunnel-8080.json -Raw | ConvertFrom-Json
Stop-Process -Id $state.tunnelPid -Force      # ssh 进程
if ($state.backendPid) { Stop-Process -Id $state.backendPid -Force }  # 脚本启动的 java 进程
```

不要按端口盲目结束进程；`backendPid` 为空表示后端是复用已有进程，脚本没启动它。

## 已知依赖

本脚本依赖工作区中**已存在但尚未提交**的跨域开关
（`zhiyu.cors.allowed-origins` / `ZHIYU_CORS_ALLOWED_ORIGINS`）。
若该改动被回退，脚本注入的 `--zhiyu.cors.allowed-origins` 会被忽略，
跨域将退回只放行 localhost / 127.0.0.1。
