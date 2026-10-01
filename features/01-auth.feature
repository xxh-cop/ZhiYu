#language: zh-CN
功能: 用户注册与登录
  作为一名在校大学生
  我希望能用学号/昵称注册并登录织愈
  以便开始情绪倾听与手作疗愈

  背景:
    假定 系统已启动，数据库已初始化

  场景: 学生成功注册
    当 学生提交注册信息：
      | username | alice |
      | password | Passw0rd! |
      | nickname | 小织 |
    那么 系统应返回 code=0，并创建学生角色账号

  场景: 用户名已存在时注册失败
    假定 用户 "alice" 已存在
    当 学生用相同 username "alice" 注册
    那么 系统应返回 code=2002，message="用户名已存在"

  场景: 注册参数缺失时校验失败
    当 学生提交缺少 password 的注册请求
    那么 系统应返回 code=1001

  场景: 学生成功登录
    假定 用户 "alice" 已注册
    当 学生用正确的 username/password 登录
    那么 系统应返回 code=0，并在 data 中携带 JWT token

  场景: 密码错误
    假定 用户 "alice" 已注册
    当 学生提交错误密码登录
    那么 系统应返回 code=2001，message="用户名或密码错误"，不签发 token

  场景: 未登录访问受保护接口
    当 客户端不带 token 访问 /api/emotion/chat
    那么 系统应返回 code=1002

  场景: 携带有效 token 查询当前用户
    假定 用户 "alice" 已登录并持有有效 token
    当 客户端携带该 token 访问 /api/auth/me
    那么 系统应返回 code=0，data 中含当前用户的 id/username/nickname/role

  场景: token 非法时查询当前用户
    当 客户端携带非法 token 访问 /api/auth/me
    那么 系统应返回 HTTP 401，且 code=1002
