# language: zh-CN
@emotion @sse @live
功能: 情绪对话句级真流式（SSE A 方案）
  作为正在倾诉的学生
  我希望小织的回复边生成边出现
  同时任何一句不安全的内容都不会真的到达我的屏幕

  背景:
    已知学生已登录并通过隐私同意
    并且存在进行中的情绪会话

  场景: 正常回复按句流式到达（happy path）
    假如客户端以 mode=live 发起 POST /api/emotion/chat
    当模型开始生成回复
    那么首句在 500 毫秒内以 chunk 事件到达
    并且后续句子按 。！？；换行 切分逐句到达
    并且每句在推送前已通过句级安全检查
    当生成结束且全文终检通过
    那么收到 done 事件且 revoked 为 false、crisis 为 false
    并且最终落库的回复与已流出内容一致

  场景: 全文终检命中时撤回并整体替换（异常路径一）
    假如客户端以 mode=live 发起 POST /api/emotion/chat
    当部分句子已以 chunk 到达
    并且全文终检命中越界需要改写
    那么收到一个 revoke 事件，包含安全替换文本与 reason=safety
    并且 done 事件的 revoked 为 true
    并且落库的是替换后的安全文本

  场景: 句级命中高危立即中断并进入危机干预（异常路径二）
    假如客户端以 mode=live 发起 POST /api/emotion/chat
    当生成中某一句命中高危词库
    那么 LLM 流立即中断，后续句子不再下发
    并且关怀提醒 care_reminder 被触发
    并且收到 revoke 事件，内容为关怀话术
    并且 done 事件的 crisis 为 true、revoked 为 true
    并且输出审计整轮只写一条且标记 revoked

  场景: 输入侧高危时不调用模型（异常路径三）
    假如客户端以 mode=live 发起 POST /api/emotion/chat
    当用户输入命中高危词库
    那么不调用 LLM
    并且以 chunk 事件直接返回关怀话术
    并且 done 事件的 crisis 为 true、revoked 为 true

  场景: 断线重发同一内容不重复落库（幂等）
    假如客户端刚发送过某条用户消息但断线未收到回复
    当以相同 content 重新发起 POST /api/emotion/chat
    那么会话中该用户消息只落库一次
    并且旧前端不传 mode 时仍收到整段单事件格式回复（兼容期）
