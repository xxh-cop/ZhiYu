# 卡点引导（stuck guidance，2026-11-05）
# 设计稿：docs/stuck-guidance-design-20261105.md
# 信号：复用 FlowDetectionService 的 DWELL_STAGNATION（同一步骤 dwell 反复回落超阈值）；
# 纯挂机 dwell 单调递增永不触发，真沉浸长停留不误伤。
Feature: 卡点引导（同一步骤反复停留时给出可拒绝的轻提示）

  Scenario: 同一步骤反复停留超阈值时，后续心跳收到卡点引导文案
    Given 用户已登录且卡点引导开关开启
    And 用户有一个进行中的编织会话
    When 用户在同一步骤的 dwell 反复回落次数超过阈值
    Then 服务端写入一条 stuck_guide_shown 触点事件
    And 后续心跳响应的 data.stuckGuide 携带一句引导文案
    And 文案为可选动作建议，不含诊断或状态判定措辞
    And 引导语生成不阻塞当次心跳响应

  Scenario: 真沉浸长停留不触发卡点引导
    Given 用户已登录且卡点引导开关开启
    And 用户有一个进行中的编织会话
    When 用户在同一步骤 dwell 单调递增并超过心流阈值
    Then 服务端判定为心流
    And 不产生 stuck_guide_shown 事件
    And 心跳响应的 data.stuckGuide 为 null

  Scenario: AI 生成失败时降级为静态模板文案
    Given 用户已登录且卡点引导开关开启
    And 卡点引导触点已触发
    When LLM 调用失败、熔断或超时
    Then 后续心跳响应的 data.stuckGuide 为静态模板文案
    And 不出现空提示或白屏

  Scenario: 用户永久关闭后任何路径不再出现引导
    Given 用户已登录
    When 用户调用 PUT /api/craft/stuck-guide/setting 设置 enabled=false
    Then user.stuck_guide_enabled 落库为 0
    And 之后同一步骤反复停留不再产生 stuck_guide_shown 事件
    And 心跳响应的 data.stuckGuide 恒为 null

  Scenario: 频率上限生效
    Given 用户已登录且卡点引导开关开启
    When 本会话 stuck_guide_shown 事件数已达 zhiyu.stuck-guide.session-max
    Then 同会话后续反复停留不再产生新触点
    When 该用户当日 stuck_guide_shown 事件数已达 zhiyu.stuck-guide.daily-max
    Then 当日任何会话都不再产生新触点

  Scenario: 客户端伪造服务端派生事件被拒
    Given 用户已登录且有一个进行中的会话
    When 客户端经 POST /api/craft/session/{id}/events 上报 event_type=stuck_guide_shown
    Then 接口返回 code=1001
