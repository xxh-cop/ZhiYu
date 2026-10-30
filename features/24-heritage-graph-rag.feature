#language: zh-CN
功能: 非遗图增强检索
  作为登录学生
  我希望问答能区分结构化关系检索与向量检索
  以便核对来源，并在向量服务未配置时仍能获得资料

  场景: 未配置向量服务时返回结构化关系检索
    假定 学生已登录且向量服务未就绪
    当 学生询问“瑞昌竹编有什么特点”
    那么 返回 retrievalMode 为 STRUCTURED_GRAPH
    并且 generated 为 false
    并且 sources 包含已登记来源与 https 链接
    并且 回答注明“未调用生成模型”

  场景: 关系与向量同时命中
    假定 向量服务已就绪
    当 学生询问“瑞昌竹编的纹样”
    那么 retrievalMode 为 GRAPH_VECTOR
    并且 sources 同时包含 VERIFIED_SOURCE 与 REFERENCE
    并且 生成回答保留来源列表

  场景: 来源下架后旧向量不再引用
    假定 旧索引中仍有已下架文件的切片
    并且 当前 classpath 已无该文件
    当 学生检索相关问题
    那么 不返回该 sourceId 的引用
    并且 其余来源仍可返回

  场景: 无资料时不编造
    当 学生询问与目录无关的问题
    那么 retrievalMode 为 NONE
    并且 sources 为空
    并且 不调用生成模型

  场景: 高危输入保留关怀话术
    当 学生输入高危表达
    那么 返回关怀话术
    并且 不读取图谱或向量资料
    并且 不生成诊断结论

  场景: 回答不声称已部署向量数据库
    当 页面展示检索模式
    那么 STRUCTURED_GRAPH 显示“结构化关系检索”
    并且 不显示“向量模型已接入”或“已部署 Chroma/Milvus”
