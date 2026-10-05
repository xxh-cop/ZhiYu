#language: zh-CN
功能: 用户隐私合规管理
  作为一名使用织愈平台的学生
  我希望能查看、同意、撤回隐私政策，并控制是否允许咨询师联系我
  以便保护我的个人信息权益

  背景:
    假定 学生已注册并登录，但尚未同意隐私政策

  场景: 查询隐私状态（默认未同意）
    当 学生访问 /api/user/privacy/status
    那么 系统应返回 code=0，data.privacyAgreed=0，data.canContact=0

  场景: 同意隐私政策
    当 学生调用 POST /api/user/privacy/agree
    那么 系统应返回 code=0
    并且 再次查询隐私状态时 privacyAgreed=1

  场景: 撤回隐私政策同意
    假定 学生已同意隐私政策
    当 学生调用 POST /api/user/privacy/withdraw
    那么 系统应返回 code=0
    并且 隐私状态中 privacyAgreed=0 且 canContact=0

  场景: 未同意隐私政策时不能开启可联系
    当 学生在未同意隐私政策时调用 PUT /api/user/can-contact 传 canContact=1
    那么 系统应返回 code=1001，message 提示需先同意隐私政策

  场景: 已同意隐私政策后开启可联系
    假定 学生已同意隐私政策
    当 学生调用 PUT /api/user/can-contact 传 canContact=1
    那么 系统应返回 code=0
    并且 隐私状态中 canContact=1

  场景: 注销账号未确认时拒绝
    当 学生调用 POST /api/user/delete 传 confirm=false
    那么 系统应返回 code=1001

  场景: 注销账号确认后匿名化并逻辑删除
    当 学生调用 POST /api/user/delete 传 confirm=true
    那么 系统应返回 code=0
    并且 用户敏感字段被匿名化（nickname=已注销用户，password 清空）
    并且 用户被逻辑删除，旧 token 失效返回 1002

  场景: 未登录访问隐私接口
    当 客户端不带 token 访问 /api/user/privacy/status
    那么 系统应返回 HTTP 401，code=1002
