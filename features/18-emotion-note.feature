#language: zh-CN
功能: 心情便签管理
  作为一名使用织愈平台的学生
  我希望能记录、查看、编辑和删除心情便签
  以便在对话后保存自己的感受，且内容仅本人可见

  背景:
    假定 学生已注册并登录

  场景: 创建心情便签
    当 学生调用 POST /api/emotion/notes 传 mood="焦虑" content="考试压力大"
    那么 系统应返回 code=0，data.id 非空
    并且 数据库中 content 列为密文而非明文

  场景: 创建便签时 content 为空
    当 学生调用 POST /api/emotion/notes 传 content=""
    那么 系统应返回 code=1001

  场景: 未登录创建便签
    当 客户端不带 token 调用 POST /api/emotion/notes
    那么 系统应返回 HTTP 401，code=1002

  场景: 分页查询本人便签
    假定 学生已创建 3 条便签
    当 学生调用 GET /api/emotion/notes 传 page=1 pageSize=10
    那么 系统应返回 code=0，data.total=3
    并且 每条记录的 excerpt 为解密后前 30 字摘要

  场景: 列表不返回别人的便签
    假定 学生A创建2条，学生B创建1条
    当 学生A调用 GET /api/emotion/notes
    那么 data.total=2，不包含学生B的便签

  场景: 查询便签详情返回解密明文
    假定 学生已创建便签 content="今天很平静"
    当 学生调用 GET /api/emotion/notes/{id}
    那么 系统应返回 code=0，data.content="今天很平静"

  场景: 查询别人的便签详情
    假定 学生A创建了一条便签
    当 学生B调用 GET /api/emotion/notes/{学生A的便签id}
    那么 系统应返回 code=1003

  场景: 查询不存在的便签
    当 学生调用 GET /api/emotion/notes/999999999
    那么 系统应返回 code=1004

  场景: 编辑自己的便签
    假定 学生已创建便签 content="原来的内容"
    当 学生调用 PUT /api/emotion/notes/{id} 传 content="修改后的内容"
    那么 系统应返回 code=0
    并且 数据库中 content 为重新加密的密文

  场景: 编辑别人的便签
    假定 学生A创建了一条便签
    当 学生B调用 PUT /api/emotion/notes/{学生A的便签id}
    那么 系统应返回 code=1003

  场景: 删除自己的便签
    假定 学生已创建便签
    当 学生调用 DELETE /api/emotion/notes/{id}
    那么 系统应返回 code=0
    并且 该便签被逻辑删除，再次查询返回 1004

  场景: 删除别人的便签
    假定 学生A创建了一条便签
    当 学生B调用 DELETE /api/emotion/notes/{学生A的便签id}
    那么 系统应返回 code=1003
