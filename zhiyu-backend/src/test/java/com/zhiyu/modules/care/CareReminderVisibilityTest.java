package com.zhiyu.modules.care;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.security.JwtUtil;
import com.zhiyu.support.AuthenticatedTestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 关怀提醒可见性：咨询师只能看到未领取（脱敏）与本人领取的数据。
 *
 * <p>未领取详情返回最小载荷供领取；领取后才展示摘要与联系方式。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CareReminderVisibilityTest {

    private static final long STUDENT_ID = 882001L;
    private static final long COUNSELOR_A = 882002L;
    private static final long COUNSELOR_B = 882003L;
    private static final long ADMIN_ID = 882004L;

    private static final long REMINDER_UNCLAIMED = 883001L;
    private static final long REMINDER_A = 883002L;
    private static final long REMINDER_B = 883003L;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void fixtures() {
        AuthenticatedTestUser.ensure(db, STUDENT_ID, 0);
        AuthenticatedTestUser.ensure(db, COUNSELOR_A, 2);
        AuthenticatedTestUser.ensure(db, COUNSELOR_B, 2);
        AuthenticatedTestUser.ensure(db, ADMIN_ID, 3);
        db.update("UPDATE user SET can_contact=1 WHERE id=?", STUDENT_ID);

        insertReminder(REMINDER_UNCLAIMED, "未领取摘要");
        insertReminder(REMINDER_A, "A 已领取摘要");
        insertReminder(REMINDER_B, "B 已领取摘要");
        insertCase(884001L, REMINDER_A, COUNSELOR_A);
        insertCase(884002L, REMINDER_B, COUNSELOR_B);
    }

    @Test
    @DisplayName("咨询师列表：未领取脱敏 + 本人领取可见，其他咨询师领取不可见")
    void counselorListHidesIdentifiersAndOthersCases() throws Exception {
        JsonNode records = listAs(COUNSELOR_A, 2).get("data").get("records");

        assertEquals(2, records.size());
        JsonNode pending = findById(records, REMINDER_UNCLAIMED);
        assertNotNull(pending);
        assertFalse(pending.get("claimed").asBoolean());
        assertTrue(pending.get("summary").isNull());
        assertTrue(pending.get("userId").isNull());
        assertTrue(pending.get("sessionId").isNull());

        JsonNode mine = findById(records, REMINDER_A);
        assertNotNull(mine);
        assertTrue(mine.get("claimed").asBoolean());
        assertTrue(mine.get("mine").asBoolean());
        assertEquals("A 已领取摘要", mine.get("summary").asText());
        assertNull(findById(records, REMINDER_B), "不得看到其他咨询师领取的个案");
    }

    @Test
    @DisplayName("咨询师打开未领取详情：最小载荷，可领取但不泄露摘要")
    void counselorCanOpenUnclaimedDetailForClaim() throws Exception {
        mockMvc.perform(get("/api/admin/care-reminders/" + REMINDER_UNCLAIMED)
                        .header("Authorization", token(COUNSELOR_A, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.claimed").value(false))
                .andExpect(jsonPath("$.data.state").value("OPEN"))
                .andExpect(jsonPath("$.data.summary").doesNotExist())
                .andExpect(jsonPath("$.data.can_contact").doesNotExist());
    }

    @Test
    @DisplayName("咨询师领取后：详情返回摘要，列表归入本人")
    void counselorClaimsThenSeesSummary() throws Exception {
        mockMvc.perform(post("/api/admin/care-reminders/" + REMINDER_UNCLAIMED + "/followups")
                        .header("Authorization", token(COUNSELOR_A, 2))
                        .contentType("application/json")
                        .content("{\"action\":\"CLAIM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/care-reminders/" + REMINDER_UNCLAIMED)
                        .header("Authorization", token(COUNSELOR_A, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimed").value(true))
                .andExpect(jsonPath("$.data.mine").value(true))
                .andExpect(jsonPath("$.data.summary").value("未领取摘要"));
    }

    @Test
    @DisplayName("咨询师访问他人领取的详情：403 + 1003")
    void counselorCannotOpenOthersClaimedDetail() throws Exception {
        mockMvc.perform(get("/api/admin/care-reminders/" + REMINDER_B)
                        .header("Authorization", token(COUNSELOR_A, 2)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("管理员列表：全部可见且保留标识与摘要")
    void adminSeesEverything() throws Exception {
        JsonNode records = listAs(ADMIN_ID, 3).get("data").get("records");

        assertEquals(3, records.size());
        JsonNode pending = findById(records, REMINDER_UNCLAIMED);
        assertNotNull(pending);
        assertFalse(pending.get("claimed").asBoolean());
        assertEquals("未领取摘要", pending.get("summary").asText());
        assertEquals(STUDENT_ID, pending.get("userId").asLong());
    }

    private JsonNode listAs(long userId, int role) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/care-reminders")
                        .header("Authorization", token(userId, role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String token(long userId, int role) {
        AuthenticatedTestUser.ensure(db, userId, role);
        return "Bearer " + jwtUtil.createToken(userId, role);
    }

    private void insertReminder(long id, String summary) {
        db.update("INSERT INTO care_reminder(id,user_id,session_id,risk_level,summary,status,create_time,update_time,deleted)"
                        + " VALUES(?,?,?,?,?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",
                id, STUDENT_ID, 885001L, 2, summary);
    }

    private void insertCase(long id, long reminderId, long ownerId) {
        db.update("INSERT INTO care_case(id,reminder_id,owner_id,state,create_time,update_time,deleted)"
                        + " VALUES(?,?,?,'OPEN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",
                id, reminderId, ownerId);
    }

    private JsonNode findById(JsonNode records, long id) {
        for (JsonNode node : records) {
            if (node.get("id").asLong() == id) {
                return node;
            }
        }
        return null;
    }
}
