package com.zhiyu.ai.lineart;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import com.zhiyu.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ByteArrayResource;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LineartServiceTest {
 private AiClient ai; private UserMapper users; private LineartService service;
 @BeforeEach void setup() throws Exception {ai=mock(AiClient.class);users=mock(UserMapper.class);service=new LineartService(ai,new ObjectMapper(),users,new ByteArrayResource("主题 {theme}".getBytes(StandardCharsets.UTF_8)));}
 @AfterEach void cleanup(){UserContext.clear();}
 @Test void recipeUsesOnlyBoundedShapeParameters(){var p=service.parse("{\"motif\":\"leaf\",\"petals\":8,\"rings\":3}");assertTrue(p.generated());assertEquals(8,p.petals());assertEquals("leaf",p.motif());}
 @Test void unsafeMarkupAndUnboundedValuesFallBack(){for(String s:new String[]{"<svg onload='bad()'/>","{\"motif\":\"flower\",\"petals\":100000,\"rings\":2}","{\"motif\":\"flower\",\"petals\":12.5,\"rings\":2}","{\"motif\":\"flower\",\"petals\":12,\"rings\":2,\"script\":\"x\"}","{\"motif\":\"flower\",\"petals\":12,\"rings\":2} <script>"})assertFalse(service.parse(s).generated());}
 @Test void noLoginOrNoConsentNeverCallsAi(){assertThrows(BusinessException.class,()->service.generate("花",true));UserContext.set(new LoginUser(1L,0));var user=new User();user.setStatus(1);user.setPrivacyAgreed(0);when(users.selectById(1L)).thenReturn(user);assertThrows(BusinessException.class,()->service.generate("花",true));verifyNoInteractions(ai);}
 @Test void validRequestUsesExternalPromptAndPreservesGenerationFlag(){UserContext.set(new LoginUser(1L,0));var user=new User();user.setStatus(1);user.setPrivacyAgreed(1);when(users.selectById(1L)).thenReturn(user);when(ai.lineartPlan(eq("叶子"),eq("主题 叶子"))).thenReturn(new AiClient.CreativeResponse("{\"motif\":\"leaf\",\"petals\":8,\"rings\":1}",true));assertTrue(service.generate("叶子",true).generated());}
 @Test void providerFallbackIsExplicitlyNotGenerated(){UserContext.set(new LoginUser(1L,0));var user=new User();user.setStatus(1);user.setPrivacyAgreed(1);when(users.selectById(1L)).thenReturn(user);when(ai.lineartPlan(anyString(),anyString())).thenReturn(new AiClient.CreativeResponse("",false));assertFalse(service.generate("花",true).generated());}
}
