Feature: 本人成长记录回看
  Scenario: 同一量表同一版本的自评趋势
    Given 本人拥有不同量表和版本的完整记录及草稿
    When 切换时间范围和量表
    Then 图表只展示对应版本完整记录的最近12次分数
    And 可以选择每次记录查看时间和说明且不推断治疗效果

  Scenario: 缺少前测感受不补值
    Given 某次创作只有结束后的感受
    When 查看创作前后对照
    Then 开始前标为未记录而非零分
    And 页面说明开始前是结束后回顾填写

  Scenario: 记录超出加载范围
    Given 本人拥有超过365条未删除记录
    When 读取成长旅程
    Then 返回最近365条和truncated=true
    And 页面明确较早记录尚未载入且不称为完整历史

  Scenario: 活动反馈不是量表分数
    Given 本人拥有活动评分记录
    When 打开成长旅程
    Then 不将其混入自评分数图表或显示未定义的量表名称
