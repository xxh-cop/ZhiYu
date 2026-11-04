package com.zhiyu.modules.inheritor;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.craft.service.CraftProjectService;
import com.zhiyu.modules.inheritor.dto.StepCreateDTO;
import com.zhiyu.modules.inheritor.service.InheritorProjectService;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AU3（2026-11-04）：步骤音频上传闭环的服务侧测试。
 *
 * <p>覆盖：步骤音频引用校验（拒绝公开 uploads 路径 / 拒绝引用他人私有媒体）、
 * 替换与删除步骤时的孤儿音频退役（逻辑注销 + 清理任务登记，共享引用不误删）、
 * 学生端步骤列表下发的签名播放地址重写（外链与 /static/ 存量约定透传）。
 *
 * <p>media_asset 行直接 SQL 造数（校验与退役只读 DB，不产生存储 IO）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StepAudioClosureTest {

    @Autowired
    private InheritorProjectService inheritorService;
    @Autowired
    private CraftProjectService craftProjectService;
    @Autowired
    private JdbcTemplate jdbc;

    private long inheritor;
    private long otherInheritor;
    private long student;
    private long projectId;

    @BeforeEach
    void setup() {
        inheritor = 910001L;
        otherInheritor = 910002L;
        student = 910003L;
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", inheritor, "au3_inh1", "传承人甲", 1);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", otherInheritor, "au3_inh2", "传承人乙", 1);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", student, "au3_stu", "学生", 0);
        projectId = 910100L;
        jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,status,create_time,update_time,deleted)"
                + " VALUES(?,?,?,1,'纸艺',0,NOW(),NOW(),0)", projectId, inheritor, "AU3测试项目");
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    /** 造一条 media_asset 行并返回其引用串。 */
    private String mediaRef(long id, long owner) {
        jdbc.update("INSERT INTO media_asset(id,user_id,storage_key,size_bytes,create_time,update_time,deleted)"
                + " VALUES(?,?,?,100,NOW(),NOW(),0)", id, owner, "au3-" + id + ".mp3");
        return "/api/media/" + id + "/content";
    }

    private StepCreateDTO stepDto(int no, String audioUrl) {
        StepCreateDTO dto = new StepCreateDTO();
        dto.setStepNo(no);
        dto.setTitle("步骤" + no);
        dto.setGuide("引导语");
        dto.setAudioUrl(audioUrl);
        return dto;
    }

    @Test
    @DisplayName("AU3：步骤音频引用校验——拒绝公开 uploads 路径与他人私有媒体，接受本人媒体/外链/静态资源")
    void stepAudioRefValidation() {
        // 公开 uploads 路径（红线）→ 1001
        var pub = assertThrows(BusinessException.class,
                () -> inheritorService.addStep(inheritor, projectId, stepDto(1, "/uploads/audio/x.mp3")));
        assertEquals(1001, pub.getCode());
        // 他人私有媒体 → 1003
        String foreign = mediaRef(920001L, otherInheritor);
        var stolen = assertThrows(BusinessException.class,
                () -> inheritorService.addStep(inheritor, projectId, stepDto(1, foreign)));
        assertEquals(1003, stolen.getCode());
        // 非法形态 → 1001
        assertThrows(BusinessException.class,
                () -> inheritorService.addStep(inheritor, projectId, stepDto(1, "ftp://x/y.mp3")));
        // 本人私有媒体 / 外链 / 静态资源 → 通过
        String own = mediaRef(920002L, inheritor);
        assertNotNull(inheritorService.addStep(inheritor, projectId, stepDto(1, own)));
        assertNotNull(inheritorService.addStep(inheritor, projectId, stepDto(2, "https://cdn.example.com/guide.mp3")));
        assertNotNull(inheritorService.addStep(inheritor, projectId, stepDto(3, "/static/audio/101-1.mp3")));
    }

    @Test
    @DisplayName("AU3：替换步骤音频→旧媒体退役（逻辑注销+清理登记）；共享引用不误删；删除步骤退役孤儿")
    void replaceAndDeleteRetireOrphans() {
        String audioA = mediaRef(920011L, inheritor);
        String audioB = mediaRef(920012L, inheritor);
        Long step1 = inheritorService.addStep(inheritor, projectId, stepDto(1, audioA));
        // 另一项目（同一传承人）的步骤共享 audioA
        long project2 = 910101L;
        jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,status,create_time,update_time,deleted)"
                + " VALUES(?,?,?,1,'纸艺',0,NOW(),NOW(),0)", project2, inheritor, "AU3共享项目");
        inheritorService.addStep(inheritor, project2, stepDto(1, audioA));

        // 替换 step1 的音频 A→B：A 仍被 project2 引用，不得退役
        inheritorService.updateStep(inheritor, projectId, step1, stepDto(1, audioB));
        assertEquals(0, jdbc.queryForObject(
                "SELECT deleted FROM media_asset WHERE id=920011", Integer.class), "共享引用不得退役");

        // 删除 project2 的共享步骤后，A 成为孤儿；删除 step1（现引用 B）退役 B
        Long sharedStep = jdbc.queryForObject(
                "SELECT id FROM craft_step WHERE project_id=? AND deleted=0", Long.class, project2);
        inheritorService.deleteStep(inheritor, project2, sharedStep);
        assertEquals(1, jdbc.queryForObject(
                "SELECT deleted FROM media_asset WHERE id=920011", Integer.class), "孤儿音频应逻辑注销");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_cleanup_job WHERE storage_kind='PRIVATE' AND storage_key='au3-920011.mp3'",
                Integer.class), "应登记存储清理任务");

        inheritorService.deleteStep(inheritor, projectId, step1);
        assertEquals(1, jdbc.queryForObject(
                "SELECT deleted FROM media_asset WHERE id=920012", Integer.class), "删除步骤应退役其音频");
    }

    @Test
    @DisplayName("AU3：删除项目级联退役步骤音频（全部步骤删除后再判定引用）")
    void deleteProjectRetiresStepAudios() {
        String audio = mediaRef(920021L, inheritor);
        inheritorService.addStep(inheritor, projectId, stepDto(1, audio));
        inheritorService.addStep(inheritor, projectId, stepDto(2, audio)); // 同项目两步共享
        inheritorService.deleteProject(inheritor, projectId);
        assertEquals(1, jdbc.queryForObject(
                "SELECT deleted FROM media_asset WHERE id=920021", Integer.class), "项目删除后共享音频应退役");
    }

    @Test
    @DisplayName("AU3：学生端步骤列表下发签名播放地址；外链与 /static/ 透传；传承人详情预览同为签名地址")
    void studentStepsGetSignedPlaybackUrl() {
        String own = mediaRef(920031L, inheritor);
        inheritorService.addStep(inheritor, projectId, stepDto(1, own));
        inheritorService.addStep(inheritor, projectId, stepDto(2, "https://cdn.example.com/guide.mp3"));
        inheritorService.addStep(inheritor, projectId, stepDto(3, "/static/audio/101-1.mp3"));
        // 上架后学生可读
        jdbc.update("UPDATE craft_project SET status=1 WHERE id=?", projectId);
        UserContext.set(new LoginUser(student, 0));
        var steps = craftProjectService.listSteps(projectId);
        assertEquals(3, steps.size());
        String signed = steps.get(0).getAudioUrl();
        assertTrue(signed.startsWith("/api/media/920031/content?")
                        && signed.contains("uid=" + student) && signed.contains("signature="),
                "私有媒体引用应换签名播放地址: " + signed);
        assertEquals("https://cdn.example.com/guide.mp3", steps.get(1).getAudioUrl(), "外链透传");
        assertEquals("/static/audio/101-1.mp3", steps.get(2).getAudioUrl(), "前端包静态资源透传");

        // 传承人预览本人项目（项目保持已上架）：owner 授权分支同样换签名
        UserContext.set(new LoginUser(inheritor, 1));
        var detail = inheritorService.getProjectDetail(inheritor, projectId);
        assertTrue(detail.getSteps().get(0).getAudioUrl().contains("signature="), "传承人预览应得签名地址");
    }
}
