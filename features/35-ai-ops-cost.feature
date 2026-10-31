#language: zh-CN
功能: AI 运营成本与模型版本
  作为管理员
  我希望看到陪伴调用的 token、成本估算与模型/Prompt 版本
  以便解释一次会话并发现版本或降级异常

  场景: 陪伴调用记录 token
    当 真实模型完成一次陪伴计划
    那么 ai_trace 记录模型名、prompt_version、input_tokens、output_tokens 与延迟
    并且 不记录用户原文、工具参数或思维链

  场景: 成本按配置费率估算
    当 服务端配置每百万 token 的输入/输出费率
    那么 运营页按窗口内 token 汇总估算成本
    并且 未配置费率时只显示 token，不编造成本

  场景: 模型版本可解释
    当 管理员打开 AI 治理
    那么 显示当前模型名、temperature、maxTokens、模型/Prompt 配置修订
    并且 显示 trace 中出现过的模型版本与 Prompt 版本
    并且 模型名、地址与密钥只由服务端环境变量管理

  场景: 历史降级记录不混淆模型版本
    当 trace 中仍有早期 MOCK/EXTERNAL 模式值
    那么 模型版本统计不把它们当作模型名
    并且 保留真实模型名统计

  场景: 不展示原文
    当 返回成本与版本聚合
    那么 不返回 user_id、practice_session_id、raw_input、ai_output 或工具参数
