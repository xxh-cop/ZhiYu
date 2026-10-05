#language: zh-CN
功能: 心理援助热线公开接口
  作为一名访客或学生
  我希望能在登录页或危机干预页查看心理援助热线号码
  以便在需要时寻求专业帮助

  背景:
    假定 系统已启动，危机干预配置已加载

  场景: 未登录获取心理援助热线
    当 客户端不带 token 访问 /api/crisis/hotlines
    那么 系统应返回 code=0
    并且 data 中包含 psychCenterPhone、nationalHotline、psychCenterName、nationalHotlineName

  场景: 已登录获取心理援助热线
    假定 学生已登录并持有有效 token
    当 学生访问 /api/crisis/hotlines
    那么 系统应返回 code=0
    并且 data.psychCenterPhone 不为空
    并且 data.nationalHotline 不为空

  场景: 热线配置来自 application.yml
    那么 psychCenterPhone 应等于配置中的 zhiyu.crisis.psych-center-phone
    并且 nationalHotline 应等于配置中的 zhiyu.crisis.national-hotline
