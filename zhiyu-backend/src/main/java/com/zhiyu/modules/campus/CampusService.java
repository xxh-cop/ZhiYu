package com.zhiyu.modules.campus;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.BusinessAccess;
import com.zhiyu.modules.care.OperationLog;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CampusService {
    private final JdbcTemplate jdbc;
    private final BusinessAccess access;
    private final AesEncryptor crypto;
    private final ObjectMapper json;
    private final OperationLog audit;
    private final com.zhiyu.modules.notification.service.NotificationService notificationService;
    public record Application(@NotBlank @Size(max=100) String school, @NotBlank @Size(max=100) String college,
        @NotBlank @Size(max=40) String studentNumber, @NotBlank @Size(max=40) String realName) {}
    public record Review(@Pattern(regexp="APPROVED|REJECTED") @NotNull String decision, @NotBlank @Size(max=300) String reason) {}
    public Map<String,Object> mine() {
        var rows=jdbc.queryForList("SELECT * FROM campus_verification WHERE user_id=? AND deleted=0",access.user());
        return rows.isEmpty()?null:present(rows.get(0));
    }
    @Transactional
    public void apply(Application r) {
        Long uid=access.consentedUser();
        jdbc.queryForObject("SELECT id FROM user WHERE id=? FOR UPDATE",Long.class,uid);
        var rows=jdbc.queryForList("SELECT id,status FROM campus_verification WHERE user_id=?",uid);
        if(!rows.isEmpty() && List.of("APPROVED","PENDING").contains(rows.get(0).get("status")))
            throw new BusinessException(3001,"申请已通过或正在审核，请勿重复提交");
        String cipher;
        try {cipher=crypto.encrypt(json.writeValueAsString(Map.of("studentNumber",r.studentNumber().trim(),"realName",r.realName().trim())));}
        catch(Exception e){throw new BusinessException(5000,"身份信息暂时无法保存");}
        if(rows.isEmpty()) jdbc.update("INSERT INTO campus_verification(id,user_id,school,college,identity_cipher,status,create_time,update_time,deleted) VALUES(?,?,?,?,?,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",IdWorker.getId(),uid,r.school().trim(),r.college().trim(),cipher);
        else jdbc.update("UPDATE campus_verification SET school=?,college=?,identity_cipher=?,status='PENDING',review_reason='',reviewer_id=NULL,deleted=0,update_time=CURRENT_TIMESTAMP WHERE user_id=?",r.school().trim(),r.college().trim(),cipher,uid);
    }
    /** 核验状态合法取值（与库中定义一致：apply 写 PENDING，review 写 APPROVED/REJECTED）。 */
    private static final Set<String> ALLOWED_STATUS = Set.of("PENDING","APPROVED","REJECTED");

    /**
     * AG1（2026-11-04）：可选 status 过滤。不传/空白 → 行为与原实现完全一致；
     * 非法值 → 1001（与本类 offset() 的校验风格一致，走 GlobalExceptionHandler 400）。
     */
    public Map<String,Object> list(int page, String status) {
        access.role(3);
        String filter = normalizeStatus(status);
        String where = filter == null ? "WHERE deleted=0" : "WHERE deleted=0 AND status=?";
        Object[] filterArgs = filter == null ? new Object[]{} : new Object[]{filter};
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM campus_verification " + where, Long.class, filterArgs);
        Object[] pageArgs = filter == null ? new Object[]{offset(page)} : new Object[]{filter, offset(page)};
        var rows = jdbc.queryForList("SELECT * FROM campus_verification " + where
                + " ORDER BY update_time DESC,id DESC LIMIT 20 OFFSET ?", pageArgs);
        return Map.of("records",rows.stream().map(this::present).toList(),"total",total==null?0:total.intValue());
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String s = status.trim().toUpperCase();
        if (!ALLOWED_STATUS.contains(s)) throw new BusinessException(1001,"status 取值仅支持 PENDING/APPROVED/REJECTED");
        return s;
    }
    @Transactional
    public void review(Long id,Review r) {
        Long actor=access.role(3);
        var owner=jdbc.queryForList("SELECT user_id FROM campus_verification WHERE id=? AND deleted=0",Long.class,id);
        int changed=jdbc.update("UPDATE campus_verification SET status=?,review_reason=?,reviewer_id=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING' AND deleted=0",r.decision(),r.reason().trim(),actor,id);
        if(changed!=1) throw new BusinessException(3001,"申请已被处理，请刷新列表");
        audit.record("campus."+r.decision(),id);
        // AH3（2026-11-04）：审核结果触达申请人（驳回带理由）；通知失败不阻断审核主流程
        if(!owner.isEmpty()){
            try{notificationService.notifyCampusAudit(owner.get(0),id,"APPROVED".equals(r.decision()),r.reason());}
            catch(Exception e){org.slf4j.LoggerFactory.getLogger(CampusService.class).warn("核验结果通知写入失败（不阻断审核）: id={}, err={}",id,e.getMessage());}
        }
    }
    private Map<String,Object> present(Map<String,Object> row) {
        var result=new LinkedHashMap<String,Object>();
        for(String k:List.of("id","school","college","status","review_reason","create_time","update_time")) result.put(k,row.get(k));
        result.put("id",String.valueOf(row.get("id")));
        try {result.put("identity",json.readValue(crypto.decrypt((String)row.get("identity_cipher")),Map.class));}
        catch(Exception e){result.put("identity",Map.of("realName","信息无法读取","studentNumber","请联系管理员"));}
        return result;
    }
    public static int offset(int page) { if(page<1||page>100000) throw new BusinessException(1001,"页码超出范围");return (page-1)*20; }
}
