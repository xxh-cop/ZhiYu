Feature: 可配置语音服务
  Scenario: 服务未配置
    Given 语音服务默认关闭
    When 用户点击语音输入
    Then 显示未配置提示并保留文字输入
    And 不申请麦克风权限
  Scenario: 已配置网关识别录音
    Given 用户同意隐私政策且语音网关可用
    When 用户提交不超过五兆的受支持音频
    Then 识别文本经过安全过滤并进入输入框供确认
    And 音频不保存到公开上传目录
  Scenario: 网关不可用
    Given 语音网关连续失败达到五次
    When 用户再次请求同类语音服务
    Then 对应资源熔断六十秒且显示文字路径提示
    And 不阻断其他 AI 资源
