#language: zh-CN
功能: 统一练习会话
  作为登录学生
  我希望把一次非遗练习保存为统一会话
  以便暂停、恢复、完成并继续回看

  场景: 创建练习会话
    当 学生选择 10 分钟和 HERITAGE 类型
    那么 返回状态 CREATED
    并且 currentStep 为 1
    并且 不生成 AI 内容
    并且 不保存原始对话

  场景: 只能读取本人会话
    假定 学生会话属于另一个账号
    当 当前学生读取该会话
    那么 返回 code 1003
    并且 不返回会话详情

  场景: 正常状态流转
    当 学生依次执行 START、PAUSE、RESUME、COMPLETE、SAVE_ARTIFACT
    那么 状态依次为 PRACTICING、PAUSED、PRACTICING、COMPLETED、ARTIFACT_SAVED
    并且 开始时间只设置一次
    并且 完成时间在 COMPLETED 时写入

  场景: 非法状态流转被拒绝
    当 学生从 CREATED 直接执行 COMPLETE
    那么 返回 code 3001
    并且 不更新会话

  场景: 放弃后可重新开始
    当 学生从 PRACTICING 执行 ABANDON
    那么 状态为 ABANDONED
    当 学生再次执行 START
    那么 状态回到 PRACTICING

  场景: 现有功能不受影响
    当 新会话表上线
    那么 原情绪会话、创作会话、成长旅程与社区仍可访问
    并且 不删除或重命名旧表
