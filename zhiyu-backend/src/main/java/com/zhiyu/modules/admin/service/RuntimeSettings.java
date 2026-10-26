package com.zhiyu.modules.admin.service;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.BusinessAccess;
import com.zhiyu.modules.care.OperationLog;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor
public class RuntimeSettings {
    private final JdbcTemplate db;private final ObjectMapper json;private final BusinessAccess access;
    public Map<String,Object> defaults(String key){return switch(key){case "recommendation"->Map.of("count",3,"tagWeight",0.6,"easeWeight",0.4,"maxSafety","LOW");case "model"->Map.of("temperature",0.7,"maxTokens",800);case "prompts"->Map.of("companion","","recommendation","","work","");default->throw new BusinessException(1001,"不支持的配置类型");};}
    public Map<String,Object> values(String key){var rows=db.queryForList("SELECT payload FROM runtime_config WHERE config_key=? AND deleted=0",key);if(rows.isEmpty())return defaults(key);try{return json.readValue(rows.get(0).get("payload").toString(),new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("运行配置读取失败",e);}}
    public List<Map<String,Object>> list(){access.role(3);return List.of("recommendation","model","prompts").stream().map(key->{var rev=db.queryForList("SELECT revision FROM runtime_config WHERE config_key=? AND deleted=0",Integer.class,key);return Map.<String,Object>of("key",key,"revision",rev.isEmpty()?0:rev.get(0),"value",values(key));}).toList();}
    public double number(String key,String field){return ((Number)values(key).get(field)).doubleValue();}
    public String prompt(String key){return values("prompts").getOrDefault(key,"").toString();}
    private void range(Map<String,Object> v,String field,double min,double max,boolean integer){Object value=v.get(field);if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max||(integer&&n.doubleValue()!=n.intValue()))throw new BusinessException(1001,"配置参数超出允许范围："+field);}
    @Transactional public void save(String key,int revision,Map<String,Object> value){long uid=access.role(3);var expected=defaults(key);if(value==null||!value.keySet().equals(expected.keySet()))throw new BusinessException(1001,"配置字段不完整或包含不支持的字段");if(key.equals("model")){range(value,"temperature",0,1,false);range(value,"maxTokens",128,2048,true);}else if(key.equals("recommendation")){range(value,"count",1,6,true);range(value,"tagWeight",0,2,false);range(value,"easeWeight",0,2,false);if(!List.of("LOW","GUIDED").contains(value.get("maxSafety")))throw new BusinessException(1001,"安全范围只能为 LOW 或 GUIDED");}else for(Object v:value.values())if(!(v instanceof String s)||s.length()>2000)throw new BusinessException(1001,"提示扩展最长 2000 字");
        // Serialize first creation as well as optimistic updates across administrator accounts.
        db.queryForObject("SELECT id FROM user WHERE role=3 AND deleted=0 ORDER BY id LIMIT 1 FOR UPDATE",Long.class);
        String payload;try{payload=json.writeValueAsString(value);}catch(Exception e){throw new BusinessException(1001,"配置无法编码");}
        var rows=db.queryForList("SELECT revision FROM runtime_config WHERE config_key=? FOR UPDATE",Integer.class,key);if((rows.isEmpty()?0:rows.get(0))!=revision)throw new BusinessException(3001,"配置已由其他管理员更新，请重新读取后修改");if(rows.isEmpty())db.update("INSERT INTO runtime_config(id,config_key,payload,revision) VALUES(?,?,?,1)",IdWorker.getId(),key,payload);else db.update("UPDATE runtime_config SET payload=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE config_key=?",payload,key);
        db.update("INSERT INTO admin_operation_log(id,actor_id,action,target_id,create_time,update_time,deleted) VALUES(?,?,?,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",IdWorker.getId(),uid,"CONFIG_UPDATE_"+key+"_v"+(revision+1));
    }
}
