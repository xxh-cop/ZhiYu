package com.zhiyu.modules.auth.mapper;

import com.zhiyu.modules.auth.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserMapper} 单元测试（H2 + MySQL 模式）。
 *
 * <p>对应 {@code docs/task-breakdown.md} T0.2 的 Done 标准：
 * User 表 CRUD 可用，且 {@code create_time / update_time / deleted} 由框架自动维护。
 *
 * <p>每个测试方法都跑在事务里并自动回滚，方法之间互不影响，
 * 也不依赖 {@code sql/init-data.sql} 的种子数据（单测只建表、不导数据）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("UserMapper：user 表 CRUD 与字段自动维护")
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    /** 用于绕过实体上 {@code @TableField(select = false)} 直接核对逻辑删除标记。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 造一个只设业务字段的用户：id / create_time / update_time / deleted 全部留给框架填。 */
    private User newUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("$2a$10$abcdefghijklmnopqrstuvwx");
        user.setNickname("测试昵称");
        user.setSchool("测试大学");
        user.setRole(0);
        user.setStatus(1);
        user.setCanContact(0);
        return user;
    }

    @Test
    @DisplayName("insert：主键由应用侧雪花算法生成（不是 DDL 自增的小整数）")
    void insert_主键由雪花算法生成() {
        User user = newUser("u_mapper_snowflake");
        userMapper.insert(user);

        Long id = user.getId();
        assertThat(id).isNotNull();
        assertThat(id).isPositive();
        // 雪花 ID 是 19 位量级；种子数据用的是 1..4 这种手写小整数，这里必须明显更大
        assertThat(id).isGreaterThan(1_000_000L);
    }

    @Test
    @DisplayName("insert：create_time / update_time 由 MetaObjectFillHandler 自动填充")
    void insert_自动填充时间字段() {
        User user = newUser("u_mapper_fill");
        userMapper.insert(user);

        User saved = userMapper.selectById(user.getId());
        assertThat(saved).isNotNull();
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isAfterOrEqualTo(saved.getCreateTime());
    }

    @Test
    @DisplayName("selectById：字段回读与写入一致（验证下划线列名 ↔ 驼峰属性映射）")
    void selectById_字段回读一致() {
        User user = newUser("u_mapper_readback");
        user.setAvatarUrl("https://example.com/a.png");
        user.setCraftPreferences("专注,静心");
        userMapper.insert(user);

        User saved = userMapper.selectById(user.getId());
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isEqualTo(user.getId());
        assertThat(saved.getUsername()).isEqualTo("u_mapper_readback");
        assertThat(saved.getPassword()).isEqualTo("$2a$10$abcdefghijklmnopqrstuvwx");
        assertThat(saved.getNickname()).isEqualTo("测试昵称");
        assertThat(saved.getAvatarUrl()).isEqualTo("https://example.com/a.png");
        assertThat(saved.getSchool()).isEqualTo("测试大学");
        assertThat(saved.getCraftPreferences()).isEqualTo("专注,静心");
        assertThat(saved.getRole()).isZero();
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getCanContact()).isZero();
    }

    @Test
    @DisplayName("deleteById：逻辑删除 —— 常规查询查不到，但物理行仍在且 deleted=1")
    void deleteById_逻辑删除() {
        User user = newUser("u_mapper_logic_delete");
        userMapper.insert(user);
        Long id = user.getId();

        int rows = userMapper.deleteById(id);
        assertThat(rows).isEqualTo(1);

        // 逻辑删除后，MyBatis-Plus 自动追加 deleted = 0，常规查询查不到
        assertThat(userMapper.selectById(id)).isNull();

        // 但物理行还在，只是 deleted 被置 1（@TableField(select = false) 读不到，走原生 SQL）
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM user WHERE id = ?", Integer.class, id);
        assertThat(deleted).isEqualTo(1);
    }
}
