# language: zh-CN
@craft @flow @metrics
功能: 心流度量修复（灌水修正 + 疑似离席只标记不扣除）与创作会话行为事件表
  flow_seconds 只计入跨过心流阈值（300s）之后的停留时长，按心跳切片累加，
  刚达标的步骤不再白送五分钟。挂机判定不要求输入事件（手在忙、手机没动正是沉浸），
  强信号走生命周期；仅当同一步骤停留远超同类步骤 P95 且页面可见但窗口内
  无任何生命周期事件时标记 suspected_away，且只标记、不从 flow_seconds 扣除。
  事件表 craft_session_event 补齐步骤时间线、焦躁双信号（dwell_stagnation 与
  step_backtrack 分开记录，产品裁决前不合并）、呼吸响应与结束事件等数据源；
  写入幂等（client_seq 去重），失败不影响业务主流程。

  背景:
    假设存在已上架项目 P（status=1，2 个步骤）
    并且学生 S 在项目 P 有进行中会话（status=1）

  @happy-path
  场景: 心流计时只计跨阈值后时长，切片累加
    当学生 S 在步骤 1 连续停留并按时心跳上报 dwell=300
    那么判定进入心流（inFlow=true），本次 flowDelta=0（刚达标不白送 300 秒）
    当 dwell 继续推进到 310、320
    那么两次心跳的 flowDelta 分别为 10、10
    并且会话 flow_seconds 累计为 20（恰好是阈值后的 20 秒）

  @happy-path
  场景: 客户端批量上报生命周期事件，重复 client_seq 不重复入库
    当学生 S 批量上报 2 条事件（visibility_change seq=1、audio_play seq=2）
    那么返回 accepted=2、deduped=0
    当学生 S 原样重放同一批次
    那么返回 accepted=0、deduped=2
    并且 craft_session_event 中该会话仍只有 2 行

  @happy-path
  场景: 心跳派生事件落表，焦躁双信号分开记录
    当学生 S 从步骤 1 前进到步骤 2
    那么写入 step_leave（step 1，带最终停留秒数）、step_enter（step 2）、step_advance 事件
    当学生 S 从步骤 2 回退到步骤 1
    那么写入 step_backtrack 事件
    当学生 S 在同一步骤 dwell 反复回落超过阈值次数
    那么写入 dwell_stagnation 事件，且与 step_backtrack 分别命名、互不合并

  @happy-path
  场景: 远超 P95 且无窗口生命周期事件 → 标记疑似离席但不扣心流
    假设项目 P 步骤 1 已有足够 step_leave 停留样本（P95 估计可用）
    并且学生 S 的会话具备生命周期上报能力且当前停留窗口内无任何生命周期事件
    当学生 S 在步骤 1 的 dwell 远超 P95 × 2 时上报心跳
    那么写入 suspected_away 事件（同一停留窗口只标一次）
    并且 flow_seconds 仍按心流口径正常累加，不扣除疑似离席时长

  @error-path
  场景: 契约违规的批量上报被拒
    当学生 S 上报未知事件类型或 dwell_stagnation、suspected_away 等服务端派生类型
    那么整批请求被拒（code=1001）
    当学生 S 上报的 context 含嵌套结构或超 64 字符的字符串值
    那么整批请求被拒（code=1001）

  @error-path
  场景: 非归属者与终态会话的事件上报被拒
    当其他学生对会话 S 上报事件
    那么被拒（code=1003）
    当学生 S 对已完成会话上报事件
    那么被拒（code=3001）

  @error-path
  场景: 发生时刻超前服务端超过 5 分钟 → 该条丢弃计 failed
    当学生 S 上报一条 occurredAt 超前服务端 10 分钟的事件
    那么返回 accepted=0、failed=1，事件不入库
