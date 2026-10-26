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
    public Map<String,Object> list(int page) {
        access.role(3);
        return Map.of("records",jdbc.queryForList("SELECT * FROM campus_verification WHERE deleted=0 ORDER BY update_time DESC,id DESC LIMIT 20 OFFSET ?",offset(page)).stream().map(this::present).toList(),"total",jdbc.queryForObject("SELECT COUNT(*) FROM campus_verification WHERE deleted=0",Long.class));
    }
    @Transactional
    public void review(Long id,Review r) {
        Long actor=access.role(3);
        int changed=jdbc.update("UPDATE campus_verification SET status=?,review_reason=?,reviewer_id=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING' AND deleted=0",r.decision(),r.reason().trim(),actor,id);
        if(changed!=1) throw new BusinessException(3001,"申请已被处理，请刷新列表");
        audit.record("campus."+r.decision(),id);
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
