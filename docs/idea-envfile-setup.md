# IDEA 读取 .env 文件配置指南

## 为什么需要这个

IDEA 启动 Spring Boot 时不读 PowerShell 的 `$env:` 变量，
导致后端用了 MockLlmClient（固定话术）。
装 EnvFile 插件后，IDEA 启动时会自动读取 .env 文件。

## 一次性配置步骤

### 1. 安装 EnvFile 插件

- File → Settings → Plugins
- 搜索 `EnvFile`
- 安装（作者 Sergey Egorov 的那个）
- 重启 IDEA

### 2. 创建 .env 文件

- 复制仓库根的 `.env.example` 为 `.env`
- 用真实 key 替换 `sk-your-real-key-here`

### 3. 配置 Run Configuration

- Run → Edit Configurations
- 选中 ZhiyuApplication
- 找到 "Enable EnvFile" 复选框（装了插件后会出现）
- 勾选 → 点 + → 选 .env 文件 → 指向仓库根 `.env`
- 点 OK

### 4. 验证

- IDEA 点绿色三角启动
- 日志应显示 `SpringAiLlmClient 初始化完成（DeepSeek API...）`
- 如果仍是 `MockLlmClient` → 检查 .env 文件路径是否正确

## 备选方案：命令行启动

如果不装插件，用 PowerShell 启动：

```powershell
cd D:\develop\Zhi_Yu
.\start-backend.ps1
```
