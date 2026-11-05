package com.zhiyu.modules.admin.controller;
import com.zhiyu.common.result.Result;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.BusinessAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
@RestController @RequiredArgsConstructor
public class ProjectCatalogController {
 private final JdbcTemplate db;private final BusinessAccess access;
 @GetMapping("/api/admin/project-catalog") public Result<?> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="0") int status,@RequestParam(defaultValue="") String category,@RequestParam(defaultValue="") String keyword){access.role(3);if(page<1||page>100000||status<0||status>3||category.length()>100||keyword.length()>100)throw new BusinessException(1001,"筛选参数无效");String where=" FROM craft_project WHERE deleted=0 AND status=?"+(category.isBlank()?"":" AND category=?")+(keyword.isBlank()?"":" AND title LIKE ?");var args=new ArrayList<Object>();args.add(status);if(!category.isBlank())args.add(category);if(!keyword.isBlank())args.add("%"+keyword+"%");long total=db.queryForObject("SELECT COUNT(*)"+where,Long.class,args.toArray());args.add((page-1)*10);var rows=db.queryForList("SELECT id,title,category,status,level,est_minutes estMinutes,video_url videoUrl,create_time createTime"+where+" ORDER BY update_time DESC,id DESC LIMIT 10 OFFSET ?",args.toArray());for(var r:rows)r.put("id",r.get("id").toString());return Result.success(Map.of("records",rows,"total",total));}
}
