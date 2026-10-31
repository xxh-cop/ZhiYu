#language: zh-CN
功能: 小织受控陪伴消息流
  作为登录学生
  我希望在练习会话里收到类型化的小织回复
  以便知道当前阶段、工具结果和引用，而不暴露模型内部推理

  场景: 推荐阶段返回类型化事件
    假定 学生有一个 RECOMMENDED 状态的练习会话
    当 学生发送“想找点事做”
    那么 响应为 text/event-stream
    并且 依次收到 stage、message、tool、done 事件
    并且 tool 事件只包含白名单工具的只读结果
    并且 不包含思维链、工具原始参数或用户原文回显

  场景: 检索结果附带可核验引用
    假定 练习会话处于 LISTENING
    当 学生问起瑞昌竹编
    那么 可以收到 tool 事件 searchHeritage
    并且 citation 事件只包含已登记来源与 citationType=VERIFIED_SOURCE
    并且 不返回未审核的检索片段

  场景: 高危输入只返回支持话术
    当 学生输入自伤或绝望相关内容
    那么 只收到 stage DONE、message 支持话术与 done blocked=true
    并且 不执行任何工具
    并且 不生成诊断或咨询结论

  场景: 越权访问被拒绝
    当 学生请求另一个账号的练习会话
    那么 返回 code 1003
    并且 不建立 SSE 连接

  场景: 断流后可安全重试
    当 客户端在收到 done 前断开
    那么 可以使用同一请求重试
    并且 不产生重复写入或重复扣费
