package com.zhiyu.modules.auth;

import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.security.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class PersonalDataExportTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired UserMapper users;
    @Autowired JwtUtil jwt;
    @Autowired AesEncryptor crypto;
    long uid;
    String token;
    @BeforeEach void setup() {
        User u = new User(); u.setUsername("export-test"); u.setPassword("secret-hash");
        u.setNickname("本人"); u.setRole(0); u.setStatus(1); u.setPrivacyAgreed(0); u.setCanContact(0);
        users.insert(u); uid=u.getId(); token="Bearer "+jwt.createToken(uid,0);
    }
    @Test void profileExcludesCredentialsWithoutRequiringConsent() throws Exception {
        mvc.perform(get("/api/user/data-export").param("category","profile").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.rows[0].id").value(Long.toString(uid)))
                .andExpect(jsonPath("$.data.rows[0].password").doesNotExist())
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }
    @Test void cursorHasNoTruncationAndNeverIncludesOtherOwnerOrDeletedNotes() throws Exception {
        for (long i=1;i<=101;i++) db.update("INSERT INTO emotion_note(id,user_id,content,deleted) VALUES(?,?,?,0)",800000+i,uid,crypto.encrypt("note-"+i));
        db.update("INSERT INTO emotion_note(id,user_id,content,deleted) VALUES(?,?,?,0)",900001,uid+1,crypto.encrypt("他人"));
        db.update("INSERT INTO emotion_note(id,user_id,content,deleted) VALUES(?,?,?,1)",900002,uid,crypto.encrypt("已删除"));
        mvc.perform(get("/api/user/data-export").param("category","notes").header("Authorization",token))
                .andExpect(jsonPath("$.data.rows.length()").value(100)).andExpect(jsonPath("$.data.rows[0].content").value("note-1"))
                .andExpect(jsonPath("$.data.nextCursor").value("800100")).andExpect(jsonPath("$.data.hasMore").value(true));
        mvc.perform(get("/api/user/data-export").param("category","notes").param("after","800100").header("Authorization",token))
                .andExpect(jsonPath("$.data.rows.length()").value(1)).andExpect(jsonPath("$.data.hasMore").value(false));
    }
    @Test void messageOwnershipUsesSessionAndGrowthPayloadIsDecrypted() throws Exception {
        db.update("INSERT INTO emotion_session(id,user_id,summary,deleted) VALUES(?,?,?,0)",800001,uid,crypto.encrypt("摘要"));
        db.update("INSERT INTO emotion_session(id,user_id,deleted) VALUES(?,?,0)",800002,uid+1);
        db.update("INSERT INTO emotion_message(id,session_id,role,content,deleted) VALUES(?,?, 'user',?,0)",810001,800001,crypto.encrypt("本人消息"));
        db.update("INSERT INTO emotion_message(id,session_id,role,content,deleted) VALUES(?,?, 'user',?,0)",810002,800002,crypto.encrypt("他人消息"));
        db.update("INSERT INTO wellbeing_record(id,user_id,kind,record_key,payload,deleted) VALUES(?,?,'MOOD','test',?,0)",820001,uid,crypto.encrypt("{}"));
        mvc.perform(get("/api/user/data-export").param("category","messages").header("Authorization",token))
                .andExpect(jsonPath("$.data.rows.length()").value(1)).andExpect(jsonPath("$.data.rows[0].content").value("本人消息"));
        mvc.perform(get("/api/user/data-export").param("category","sessions").header("Authorization",token))
                .andExpect(jsonPath("$.data.rows[0].summary").value("摘要"));
        mvc.perform(get("/api/user/data-export").param("category","records").header("Authorization",token))
                .andExpect(jsonPath("$.data.rows[0].payload").value("{}"));
    }
    @Test void invalidRequestsAndAnonymousCannotExport() throws Exception {
        mvc.perform(get("/api/user/data-export").param("category","profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/user/data-export").param("category","user").header("Authorization",token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/user/data-export").param("category","notes").param("after","-1").header("Authorization",token)).andExpect(status().isBadRequest());
    }
    @Test void worksIncludePrivateRecordsButExcludeMediaLocationsAndOtherOwners() throws Exception {
        long projectId=9007199254740993L;
        db.update("INSERT INTO work(id,user_id,project_id,title,message,visible,status,cover_url,video_url,deleted) VALUES(?,?,?,'我的私密作品','公开寄语',0,0,'private.jpg','signed-secret',0)",840001,uid,projectId);
        db.update("INSERT INTO work(id,user_id,title,deleted) VALUES(?,?,'他人作品',0)",840002,uid+1);
        db.update("INSERT INTO work(id,user_id,title,deleted) VALUES(?,?,'已撤回',1)",840003,uid);
        mvc.perform(get("/api/user/data-export").param("category","works").header("Authorization",token))
                .andExpect(jsonPath("$.data.rows.length()").value(1))
                .andExpect(jsonPath("$.data.rows[0].project_id").value(Long.toString(projectId)))
                .andExpect(jsonPath("$.data.rows[0].message").value("公开寄语"))
                .andExpect(jsonPath("$.data.rows[0].visible").value(0))
                .andExpect(jsonPath("$.data.rows[0].cover_url").doesNotExist())
                .andExpect(jsonPath("$.data.rows[0].video_url").doesNotExist())
                .andExpect(jsonPath("$.data.rows[0].user_id").doesNotExist());
    }
    @Test void bookingsPaginateAndRetainOwnRecordWhenProgramIsUnavailable() throws Exception {
        db.update("INSERT INTO learning_program(id,owner_id,kind,title,description,location,co_leader,materials,start_time,end_time,capacity,status,deleted) VALUES(850001,?,'GUIDANCE','已取消预约','说明','校内','老师','无',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,'CANCELLED',0)",uid+1);
        db.update("INSERT INTO learning_program(id,owner_id,kind,title,description,location,co_leader,materials,start_time,end_time,capacity,status,deleted) VALUES(850002,?,'GROUP','不可泄露标题','说明','校内','老师','无',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,5,'DRAFT',1)",uid+1);
        for(long i=1;i<=101;i++) db.update("INSERT INTO program_booking(id,user_id,program_id,status,deleted) VALUES(?,?,?,'CANCELLED',0)",860000+i,uid,i==1?850001:i==2?850002:850000+i);
        db.update("INSERT INTO program_booking(id,user_id,program_id,status,deleted) VALUES(?,?,850001,'BOOKED',0)",870001,uid+1);
        db.update("INSERT INTO program_booking(id,user_id,program_id,status,deleted) VALUES(?,?,850999,'BOOKED',1)",870002,uid);
        mvc.perform(get("/api/user/data-export").param("category","bookings").header("Authorization",token))
                .andExpect(jsonPath("$.data.rows.length()").value(100))
                .andExpect(jsonPath("$.data.rows[0].program_title").value("已取消预约"))
                .andExpect(jsonPath("$.data.rows[0].program_id").value("850001"))
                .andExpect(jsonPath("$.data.rows[0].owner_id").doesNotExist())
                .andExpect(jsonPath("$.data.rows[1].program_title").doesNotExist())
                .andExpect(jsonPath("$.data.nextCursor").value("860100"));
        mvc.perform(get("/api/user/data-export").param("category","bookings").param("after","860100").header("Authorization",token))
                .andExpect(jsonPath("$.data.rows.length()").value(1)).andExpect(jsonPath("$.data.hasMore").value(false));
    }
    @Test void campusExportsOnlyOwnDecryptedIdentityAndNoReviewer() throws Exception {
        String identity="{\"realName\":\"测试同学\",\"studentNumber\":\"20260001\"}";
        db.update("INSERT INTO campus_verification(id,user_id,school,college,identity_cipher,status,review_reason,reviewer_id,create_time,update_time,deleted) VALUES(880001,?,'江西师范大学','人工智能学院',?,'APPROVED','资料一致',99,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",uid,crypto.encrypt(identity));
        db.update("INSERT INTO campus_verification(id,user_id,school,college,identity_cipher,status,create_time,update_time,deleted) VALUES(880002,?,'学校','学院',?,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",uid+1,crypto.encrypt("他人"));
        mvc.perform(get("/api/user/data-export").param("category","campus").header("Authorization",token))
                .andExpect(jsonPath("$.data.rows.length()").value(1))
                .andExpect(jsonPath("$.data.rows[0].identity").value(identity))
                .andExpect(jsonPath("$.data.rows[0].review_reason").value("资料一致"))
                .andExpect(jsonPath("$.data.rows[0].identity_cipher").doesNotExist())
                .andExpect(jsonPath("$.data.rows[0].reviewer_id").doesNotExist());
        db.update("UPDATE campus_verification SET identity_cipher='corrupted' WHERE id=880001");
        mvc.perform(get("/api/user/data-export").param("category","campus").header("Authorization",token))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.data").doesNotExist());
        db.update("UPDATE campus_verification SET deleted=1 WHERE id=880001");
        mvc.perform(get("/api/user/data-export").param("category","campus").header("Authorization",token))
                .andExpect(jsonPath("$.data.rows.length()").value(0));
    }
    @Test void deletionActuallyClearsNullableProfileFieldsAndRevokesExport() throws Exception {
        db.update("UPDATE user SET avatar_url='private.jpg',school='school',intro='intro',craft_preferences='prefs' WHERE id=?",uid);
        mvc.perform(post("/api/user/delete").header("Authorization",token).contentType("application/json").content("{\"confirm\":true}"))
                .andExpect(jsonPath("$.code").value(0));
        var row=db.queryForMap("SELECT avatar_url,school,intro,craft_preferences,password,status,deleted FROM user WHERE id=?",uid);
        for(String field: new String[]{"avatar_url","school","intro","craft_preferences"}) assertThat(row.get(field)).isNull();
        assertThat(row.get("password")).isEqualTo("");
        mvc.perform(get("/api/user/data-export").param("category","profile").header("Authorization",token)).andExpect(status().isUnauthorized());
    }
}
