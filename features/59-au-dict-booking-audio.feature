# AU 批（2026-11-04）：CARE_RISK 字典三档对齐、预约反查、步骤音频上传闭环
Feature: AU 字典对齐 + 预约反查 + 步骤音频闭环

  # ── AU1：CARE_RISK 三档 ──

  Scenario: 危机词表三档齐备且等级落在 1/2/3
    Given 平台高危词表 lexicon/high-risk.txt
    When 解析全部 # @level 组标记与关键词
    Then 档位 1 关注、2 高危、3 紧急均至少有一个关键词
    And 每个关键词的等级与所在组标记一致

  Scenario: 流式句级命中按词表真实分级
    Given 句级扫描命中「割腕」（计划/行为组）
    When 触发关怀提醒
    Then care_reminder.risk_level 落 3（紧急）
    When 句级扫描命中「活着没意思」（绝望组）
    Then care_reminder.risk_level 落 1（关注）

  Scenario: 词表缺少分级标记时不崩且保守取高档
    Given 一个词表外的命中关键词（levelOf 返回 null）
    When checkInput 或 scanStreamSentence 触发关怀
    Then risk_level 保守落 3，不抛异常

  # ── AU2：预约反查 ──

  Scenario: 管理员按预约 ID 反查
    Given 一条 program_booking 记录
    When GET /api/admin/bookings/{id}
    Then 返回预约详情（id/programId/userId/status/时间，雪花 ID 为字符串）
    And program 摘要含 title/kind/startTime/endTime/status

  Scenario: 反查不存在或非管理员
    When 管理员查询不存在的预约 ID
    Then 返回 1004
    When 学生或传承人查询任一预约 ID
    Then 返回 1003

  # ── AU3：步骤音频闭环 ──

  Scenario: 传承人上传引导音频并绑定步骤，学生按授权播放
    Given 传承人登录
    When POST /api/files/upload/audio 上传真实 MP3（≤10MB，当日未超 20 次）
    Then 返回 /api/media/{id}/content 私有引用
    When 传承人将该引用设为本人待审项目的步骤音频
    And 项目上架后学生拉取步骤列表
    Then 学生拿到的 audioUrl 是 1h 签名播放地址
    And 凭签名地址 GET /api/media/{id}/content 返回 audio/mpeg 音频流

  Scenario: 上传越权与伪装内容被拒
    Given 学生登录
    When POST /api/files/upload/audio
    Then 返回 1003（HTTP 403）
    When 传承人上传扩展名伪装但内容非音频的文件
    Then 返回 1001（HTTP 400）

  Scenario: 步骤音频引用校验与红线
    When 传承人把步骤音频设为 /uploads/ 公开路径
    Then 返回 1001（禁止退回公开 uploads）
    When 传承人把步骤音频设为他人上传的私有媒体引用
    Then 返回 1003

  Scenario: 替换或删除步骤不留孤儿音频
    Given 步骤引用音频 A，且另一项目步骤也引用 A
    When 把该步骤音频替换为 B
    Then A 不退役（仍被共享引用）
    When 另一引用步骤也删除
    Then A 被逻辑注销且登记 PRIVATE 清理任务，B 不受影响
