package com.zhiyu.config;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.UserContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import java.lang.reflect.Method;

/** A database lock makes HTTP writes atomic with account revocation across server instances.
 * SSE does not hold a connection while generating: its persistence methods lock independently. */
@Configuration
public class BusinessMutationConfig {
    @Bean public DefaultPointcutAdvisor businessMutationAdvisor(JdbcTemplate db,PlatformTransactionManager manager){
        var pointcut=new StaticMethodMatcherPointcut(){@Override public boolean matches(Method m,Class<?> type){return type.getName().startsWith("com.zhiyu.")&&type.isAnnotationPresent(RestController.class)&&!m.getReturnType().getName().endsWith("SseEmitter")&&(m.isAnnotationPresent(PostMapping.class)||m.isAnnotationPresent(PutMapping.class)||m.isAnnotationPresent(DeleteMapping.class)||m.isAnnotationPresent(PatchMapping.class));}};
        MethodInterceptor interceptor=invocation->{Long uid=UserContext.getUserId();if(uid==null)return invocation.proceed();var tx=new TransactionTemplate(manager);Object[] result=new Object[1];Throwable[] failure=new Throwable[1];
            try{tx.executeWithoutResult(status->{var users=db.queryForList("SELECT id,role FROM user WHERE id=? AND status=1 AND deleted=0 FOR UPDATE",uid);if(users.isEmpty())throw new BusinessException(1002,"账号已不可用，请重新登录");if(!java.util.Objects.equals(((Number)users.get(0).get("role")).intValue(),UserContext.get().getRole()))throw new BusinessException(1003,"角色已改变，请重新登录后操作");
                try{result[0]=invocation.proceed();}catch(Throwable e){failure[0]=e;if(!(e instanceof BusinessException))status.setRollbackOnly();}
                if(failure[0]==null&&((Number)users.get(0).get("role")).intValue()>0){String type=invocation.getThis().getClass().getSimpleName();String action=type.replace("Controller","")+"."+invocation.getMethod().getName();if(action.length()>100)action=action.substring(0,100);db.update("INSERT INTO admin_operation_log(id,actor_id,action,target_id,create_time,update_time,deleted) VALUES(?,?,?,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),uid,action);}
            });}catch(org.springframework.transaction.UnexpectedRollbackException e){if(failure[0]==null)throw e;}
            if(failure[0]!=null)throw failure[0];return result[0];};
        var advisor=new DefaultPointcutAdvisor(pointcut,interceptor);advisor.setOrder(0);return advisor;
    }
}
