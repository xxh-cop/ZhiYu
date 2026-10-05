#language: zh-CN
功能: 管理端用户管理（P1-A1）
  作为管理员
  我希望查看用户列表、编辑用户信息、切换状态、变更角色与删除用户
  以便管理平台用户

  背景:
    假定 管理员已登录管理端

  场景: 管理员查看用户列表
    当 管理员请求 GET /api/admin/users
    那么 返回 code=0，data 为用户分页列表
    而且 列表项不含 password 字段

  场景: 管理员按关键字搜索用户
    当 管理员请求 GET /api/admin/users?keyword=student
    那么 返回 code=0，列表中只包含 username 或 nickname 含 "student" 的用户

  场景: 管理员查看用户详情
    当 管理员请求 GET /api/admin/users/{id}
    那么 返回 code=0，data 包含 id / username / nickname / role / status / craftPreferences

  场景: 管理员编辑用户信息
    当 管理员发起 PUT /api/admin/users/{id}，body={ nickname: "新昵称" }
    那么 返回 code=0，data.nickname 为 "新昵称"
    而且 role / status / password / canContact 未被修改

  场景: 管理员禁用用户
    当 管理员发起 PUT /api/admin/users/{id}/status，body={ status: 0 }
    那么 返回 code=0，用户 status 变为 0

  场景: 管理员不允许禁用自己
    当 管理员对自己的 userId 发起 PUT /api/admin/users/{id}/status，body={ status: 0 }
    那么 返回 code=1001，提示"不允许操作自己的账号"

  场景: 管理员变更用户角色
    当 管理员发起 PUT /api/admin/users/{id}/role，body={ role: 1 }
    那么 返回 code=0，用户 role 变为 1（传承人）

  场景: 管理员不允许修改自己的角色
    当 管理员对自己的 userId 发起 PUT /api/admin/users/{id}/role，body={ role: 0 }
    那么 返回 code=1001，提示"不允许修改自己的角色"

  场景: 咨询师不能编辑用户（仅管理员）
    当 咨询师（role=2）发起 PUT /api/admin/users/{id}
    那么 返回 code=1003

  场景: 管理员删除用户
    当 管理员发起 DELETE /api/admin/users/{id}
    那么 返回 code=0，用户被逻辑删除

  场景: 管理员不允许删除自己
    当 管理员对自己的 userId 发起 DELETE /api/admin/users/{id}
    那么 返回 code=1001，提示"不允许删除自己的账号"

  场景: 查看不存在的用户 → 1004
    当 管理员请求 GET /api/admin/users/99999
    那么 返回 code=1004
