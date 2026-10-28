-- ============================================================
-- 20261028 演示数据包（合成数据，仅用于本地演示/验收）
--
-- 前置：已执行 sql/schema.sql + sql/init-data.sql，且已执行
--       sql/migrations/20261028_craft_recommend_event.sql。
--
-- 使用：先运行 20261028-demo-pack-cleanup.sql，再运行本文件。
-- 说明：所有 ID 使用 98xxxx 高位段；不覆盖真实用户数据。
--       作品、评论、关怀提醒均为合成文本，不含真实个人信息。
-- ============================================================

-- 演示账号 student01 已同意隐私政策，便于本地彩排；真实用户必须自行同意。
UPDATE user SET privacy_agreed=1, can_contact=0 WHERE id=4;

-- 20 名合成学生，仅用于漏斗和社区展示；不用于登录演示。
INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted) VALUES
(980001,'demo_student_01','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生01',0,1,1,0,0),
(980002,'demo_student_02','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生02',0,1,1,0,0),
(980003,'demo_student_03','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生03',0,1,1,0,0),
(980004,'demo_student_04','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生04',0,1,1,0,0),
(980005,'demo_student_05','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生05',0,1,1,0,0),
(980006,'demo_student_06','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生06',0,1,1,0,0),
(980007,'demo_student_07','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生07',0,1,1,0,0),
(980008,'demo_student_08','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生08',0,1,1,0,0),
(980009,'demo_student_09','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生09',0,1,1,0,0),
(980010,'demo_student_10','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生10',0,1,1,0,0),
(980011,'demo_student_11','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生11',0,1,1,0,0),
(980012,'demo_student_12','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生12',0,1,1,0,0),
(980013,'demo_student_13','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生13',0,1,1,0,0),
(980014,'demo_student_14','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生14',0,1,1,0,0),
(980015,'demo_student_15','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生15',0,1,1,0,0),
(980016,'demo_student_16','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生16',0,1,1,0,0),
(980017,'demo_student_17','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生17',0,1,1,0,0),
(980018,'demo_student_18','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生18',0,1,1,0,0),
(980019,'demo_student_19','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生19',0,1,1,0,0),
(980020,'demo_student_20','$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa','演示学生20',0,1,1,0,0);

-- 20 个情绪会话，分布在最近 6 天
INSERT INTO emotion_session(id,user_id,mood,status,create_time,update_time,deleted) VALUES
(981001,980001,'stressed',1,DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_SUB(NOW(), INTERVAL 2 DAY),0),
(981002,980002,'stressed',1,DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),0),
(981003,980003,'stressed',1,DATE_SUB(NOW(), INTERVAL 4 DAY),DATE_SUB(NOW(), INTERVAL 4 DAY),0),
(981004,980004,'stressed',1,DATE_SUB(NOW(), INTERVAL 5 DAY),DATE_SUB(NOW(), INTERVAL 5 DAY),0),
(981005,980005,'stressed',1,DATE_SUB(NOW(), INTERVAL 6 DAY),DATE_SUB(NOW(), INTERVAL 6 DAY),0),
(981006,980006,'stressed',1,DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0),
(981007,980007,'stressed',1,DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_SUB(NOW(), INTERVAL 2 DAY),0),
(981008,980008,'stressed',1,DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),0),
(981009,980009,'stressed',1,DATE_SUB(NOW(), INTERVAL 4 DAY),DATE_SUB(NOW(), INTERVAL 4 DAY),0),
(981010,980010,'stressed',1,DATE_SUB(NOW(), INTERVAL 5 DAY),DATE_SUB(NOW(), INTERVAL 5 DAY),0),
(981011,980011,'stressed',1,DATE_SUB(NOW(), INTERVAL 6 DAY),DATE_SUB(NOW(), INTERVAL 6 DAY),0),
(981012,980012,'stressed',1,DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0),
(981013,980013,'stressed',1,DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_SUB(NOW(), INTERVAL 2 DAY),0),
(981014,980014,'stressed',1,DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),0),
(981015,980015,'stressed',1,DATE_SUB(NOW(), INTERVAL 4 DAY),DATE_SUB(NOW(), INTERVAL 4 DAY),0),
(981016,980016,'stressed',1,DATE_SUB(NOW(), INTERVAL 5 DAY),DATE_SUB(NOW(), INTERVAL 5 DAY),0),
(981017,980017,'stressed',1,DATE_SUB(NOW(), INTERVAL 6 DAY),DATE_SUB(NOW(), INTERVAL 6 DAY),0),
(981018,980018,'stressed',1,DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0),
(981019,980019,'stressed',1,DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_SUB(NOW(), INTERVAL 2 DAY),0),
(981020,980020,'stressed',1,DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),0);

-- 15 人获得推荐
INSERT INTO craft_recommendation(id,user_id,session_id,mood,reason,create_time,update_time,deleted) VALUES
(982001,980001,981001,'stressed','演示推荐理由（合成）',DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_SUB(NOW(), INTERVAL 2 DAY),0),
(982002,980002,981002,'stressed','演示推荐理由（合成）',DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),0),
(982003,980003,981003,'stressed','演示推荐理由（合成）',DATE_SUB(NOW(), INTERVAL 4 DAY),DATE_SUB(NOW(), INTERVAL 4 DAY),0),
(982004,980004,981004,'stressed','演示推荐理由（合成）',DATE_SUB(NOW(), INTERVAL 5 DAY),DATE_SUB(NOW(), INTERVAL 5 DAY),0),
(982005,980005,981005,'stressed','演示推荐理由（合成）',DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0),
(982006,980006,981006,'stressed','演示推荐理由（合成）',DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_SUB(NOW(), INTERVAL 2 DAY),0),
(982007,980007,981007,'stressed','演示推荐理由（合成）',DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),0),
(982008,980008,981008,'stressed','演示推荐理由（合成）',DATE_SUB(NOW(), INTERVAL 4 DAY),DATE_SUB(NOW(), INTERVAL 4 DAY),0),
(982009,980009,981009,'stressed','演示推荐理由（合成）',DATE_SUB(NOW(), INTERVAL 5 DAY),DATE_SUB(NOW(), INTERVAL 5 DAY),0),
(982010,980010,981010,'stressed','演示推荐理由（合成）',DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0),
(982011,980011,981011,'stressed','演示推荐理由（合成）',DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_SUB(NOW(), INTERVAL 2 DAY),0),
(982012,980012,981012,'stressed','演示推荐理由（合成）',DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),0),
(982013,980013,981013,'stressed','演示推荐理由（合成）',DATE_SUB(NOW(), INTERVAL 4 DAY),DATE_SUB(NOW(), INTERVAL 4 DAY),0),
(982014,980014,981014,'stressed','演示推荐理由（合成）',DATE_SUB(NOW(), INTERVAL 5 DAY),DATE_SUB(NOW(), INTERVAL 5 DAY),0),
(982015,980015,981015,'stressed','演示推荐理由（合成）',DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0);

INSERT INTO craft_recommendation_item(id,recommendation_id,project_id,score,reason,create_time,update_time,deleted) VALUES
(983001,982001,940001,1.0,'演示推荐项（合成）',DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_SUB(NOW(), INTERVAL 2 DAY),0),
(983002,982002,940001,1.0,'演示推荐项（合成）',DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),0),
(983003,982003,940001,1.0,'演示推荐项（合成）',DATE_SUB(NOW(), INTERVAL 4 DAY),DATE_SUB(NOW(), INTERVAL 4 DAY),0),
(983004,982004,940001,1.0,'演示推荐项（合成）',DATE_SUB(NOW(), INTERVAL 5 DAY),DATE_SUB(NOW(), INTERVAL 5 DAY),0),
(983005,982005,940001,1.0,'演示推荐项（合成）',DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0),
(983006,982006,940001,1.0,'演示推荐项（合成）',DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_SUB(NOW(), INTERVAL 2 DAY),0),
(983007,982007,940001,1.0,'演示推荐项（合成）',DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),0),
(983008,982008,940001,1.0,'演示推荐项（合成）',DATE_SUB(NOW(), INTERVAL 4 DAY),DATE_SUB(NOW(), INTERVAL 4 DAY),0),
(983009,982009,940001,1.0,'演示推荐项（合成）',DATE_SUB(NOW(), INTERVAL 5 DAY),DATE_SUB(NOW(), INTERVAL 5 DAY),0),
(983010,982010,940001,1.0,'演示推荐项（合成）',DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0),
(983011,982011,940001,1.0,'演示推荐项（合成）',DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_SUB(NOW(), INTERVAL 2 DAY),0),
(983012,982012,940001,1.0,'演示推荐项（合成）',DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),0),
(983013,982013,940001,1.0,'演示推荐项（合成）',DATE_SUB(NOW(), INTERVAL 4 DAY),DATE_SUB(NOW(), INTERVAL 4 DAY),0),
(983014,982014,940001,1.0,'演示推荐项（合成）',DATE_SUB(NOW(), INTERVAL 5 DAY),DATE_SUB(NOW(), INTERVAL 5 DAY),0),
(983015,982015,940001,1.0,'演示推荐项（合成）',DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0);

-- 9 人点击推荐（HOME/RECOMMEND 交替）
INSERT INTO craft_recommend_event(id,user_id,recommendation_id,project_id,event_type,source,create_time,update_time,deleted) VALUES
(984001,980001,982001,940001,'CLICK','HOME',DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_SUB(NOW(), INTERVAL 2 DAY),0),
(984002,980002,982002,940001,'CLICK','RECOMMEND',DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),0),
(984003,980003,982003,940001,'CLICK','HOME',DATE_SUB(NOW(), INTERVAL 4 DAY),DATE_SUB(NOW(), INTERVAL 4 DAY),0),
(984004,980004,982004,940001,'CLICK','RECOMMEND',DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0),
(984005,980005,982005,940001,'CLICK','HOME',DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_SUB(NOW(), INTERVAL 2 DAY),0),
(984006,980006,982006,940001,'CLICK','RECOMMEND',DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),0),
(984007,980007,982007,940001,'CLICK','HOME',DATE_SUB(NOW(), INTERVAL 4 DAY),DATE_SUB(NOW(), INTERVAL 4 DAY),0),
(984008,980008,982008,940001,'CLICK','RECOMMEND',DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0),
(984009,980009,982009,940001,'CLICK','HOME',DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_SUB(NOW(), INTERVAL 2 DAY),0);

-- 7 人开始创作，其中 5 人完成
INSERT INTO craft_session(id,user_id,project_id,status,current_step,start_time,end_time,flow_seconds,create_time,update_time,deleted) VALUES
(985001,980001,940001,3,4,DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_ADD(DATE_SUB(NOW(), INTERVAL 2 DAY), INTERVAL 100 SECOND),60,DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_SUB(NOW(), INTERVAL 2 DAY),0),
(985002,980002,940001,3,4,DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_ADD(DATE_SUB(NOW(), INTERVAL 3 DAY), INTERVAL 100 SECOND),60,DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),0),
(985003,980003,940001,3,4,DATE_SUB(NOW(), INTERVAL 4 DAY),DATE_ADD(DATE_SUB(NOW(), INTERVAL 4 DAY), INTERVAL 100 SECOND),60,DATE_SUB(NOW(), INTERVAL 4 DAY),DATE_SUB(NOW(), INTERVAL 4 DAY),0),
(985004,980004,940001,3,4,DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_ADD(DATE_SUB(NOW(), INTERVAL 1 DAY), INTERVAL 100 SECOND),60,DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0),
(985005,980005,940001,3,4,DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_ADD(DATE_SUB(NOW(), INTERVAL 2 DAY), INTERVAL 100 SECOND),60,DATE_SUB(NOW(), INTERVAL 2 DAY),DATE_SUB(NOW(), INTERVAL 2 DAY),0),
(985006,980006,940001,1,2,DATE_SUB(NOW(), INTERVAL 3 DAY),NULL,0,DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),0),
(985007,980007,940001,1,2,DATE_SUB(NOW(), INTERVAL 4 DAY),NULL,0,DATE_SUB(NOW(), INTERVAL 4 DAY),DATE_SUB(NOW(), INTERVAL 4 DAY),0);

-- 3 份合成作品
INSERT INTO work(id,user_id,project_id,session_id,title,cover_url,media_type,visible,anonymous,like_count,status,create_time,update_time,deleted) VALUES
(986001,980001,940001,985001,'演示作品01','/static/library/paper.jpg','image',1,1,0,1,DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0),
(986002,980002,940001,985002,'演示作品02','/static/library/paper.jpg','image',1,1,0,1,DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0),
(986003,980003,940001,985003,'演示作品03','/static/library/paper.jpg','image',1,1,0,1,DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0);

-- 合成关怀提醒（脱敏摘要，不含身份信息）
INSERT INTO care_reminder(id,user_id,risk_level,summary,status,create_time,update_time,deleted) VALUES
(986101,980001,1,'演示用脱敏摘要：近期多次提到考试压力，已授权联系。',0,DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0);

-- 两个未来活动，学生01已预约工作坊
INSERT INTO learning_program(id,owner_id,kind,title,description,location,co_leader,materials,start_time,end_time,capacity,status,create_time,update_time,deleted) VALUES
(987001,2,'WORKSHOP','演示·纸艺放松工作坊','用纸张和折叠把注意力带回手边。','大学生活动中心 302','演示带领者','纸张、圆头剪刀',DATE_ADD(NOW(), INTERVAL 3 DAY),DATE_ADD(NOW(), INTERVAL 3 DAY) + INTERVAL 2 HOUR,12,'PUBLISHED',NOW(),NOW(),0),
(987002,2,'COURSE','演示·赣绣书签入门课','四节课完成一枚自己的书签。','线上录播','演示带领者','绣线、书签底布',DATE_ADD(NOW(), INTERVAL 7 DAY),DATE_ADD(NOW(), INTERVAL 7 DAY) + INTERVAL 2 HOUR,10,'PUBLISHED',NOW(),NOW(),0);

INSERT INTO program_booking(id,program_id,user_id,status,create_time,update_time,deleted) VALUES
(987101,987001,4,'BOOKED',NOW(),NOW(),0);

-- 学生01的已完成创作，供 AI 回顾/发布预填彩排使用
INSERT INTO craft_session(id,user_id,project_id,status,current_step,start_time,end_time,flow_seconds,create_time,update_time,deleted) VALUES
(988001,4,940001,3,4,DATE_SUB(NOW(), INTERVAL 2 HOUR),DATE_SUB(NOW(), INTERVAL 1 HOUR),1800,DATE_SUB(NOW(), INTERVAL 2 HOUR),DATE_SUB(NOW(), INTERVAL 1 HOUR),0);

-- 学生01的已发布作品与评论（2 条公开 + 1 条待审核）
INSERT INTO work(id,user_id,project_id,session_id,title,cover_url,media_type,message,visible,anonymous,like_count,status,create_time,update_time,deleted) VALUES
(988101,4,940001,988001,'期末周的一片叶子','/static/library/paper.jpg','image','把期末周的压力折进纸里，留一片安静的叶子。',1,1,0,1,DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),0);
INSERT INTO work_comment(id,work_id,user_id,content,status,create_time,update_time,deleted) VALUES
(988201,988101,980002,'这片叶子的颜色很安静。',1,DATE_SUB(NOW(), INTERVAL 12 HOUR),DATE_SUB(NOW(), INTERVAL 12 HOUR),0),
(988202,988101,980003,'我也想在期末周做一件小手作。',1,DATE_SUB(NOW(), INTERVAL 10 HOUR),DATE_SUB(NOW(), INTERVAL 10 HOUR),0),
(988203,988101,980004,'谢谢你把这段安静分享出来。',0,DATE_SUB(NOW(), INTERVAL 2 HOUR),DATE_SUB(NOW(), INTERVAL 2 HOUR),0);
