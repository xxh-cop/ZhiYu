package com.zhiyu.modules.group.controller;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.work.service.WorkAudience;
import com.zhiyu.modules.work.service.WorkService;
import com.zhiyu.security.BusinessAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class GroupWorkController {
    private final WorkAudience audience;private final WorkService works;private final JdbcTemplate db;private final BusinessAccess access;
    @GetMapping("/api/groups/{id}/works") public Result<?> list(@PathVariable long id,@RequestParam(required=false) Long before){long uid=access.user();audience.member(id,uid);return Result.success(db.queryForList("SELECT w.id FROM work w JOIN group_work g ON g.work_id=w.id AND g.deleted=0 WHERE g.group_id=? AND w.deleted=0 AND w.status=1 AND w.visible=2 AND w.id<? ORDER BY w.id DESC LIMIT 20",Long.class,id,before==null?Long.MAX_VALUE:before).stream().map(w->works.getWork(uid,w)).toList());}
}
