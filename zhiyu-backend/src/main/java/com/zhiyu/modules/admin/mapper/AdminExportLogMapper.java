package com.zhiyu.modules.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.modules.admin.entity.AdminExportLog;
import org.apache.ibatis.annotations.Mapper;

/** {@link AdminExportLog} 的 MyBatis-Plus Mapper（只追加的审计表）。 */
@Mapper
public interface AdminExportLogMapper extends BaseMapper<AdminExportLog> {
}
