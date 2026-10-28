-- ============================================================
-- 20261028 演示数据包清理
-- 仅删除 980000–989999 的合成数据；不触碰 seed 账号/项目。
-- 若彩排中领取过关怀提醒，同时清理 care_case / care_followup。
-- 注意：ai_audit_log 只追加不删除，AI 彩排产生的审计记录会保留。
-- ============================================================

DELETE FROM care_followup WHERE reminder_id=986101;
DELETE FROM care_case WHERE reminder_id=986101;
DELETE FROM care_reminder WHERE id BETWEEN 980000 AND 989999;

DELETE FROM program_booking WHERE id BETWEEN 980000 AND 989999 OR program_id BETWEEN 980000 AND 989999;
DELETE FROM learning_program WHERE id BETWEEN 980000 AND 989999;

DELETE FROM work_comment WHERE work_id BETWEEN 980000 AND 989999;
DELETE FROM work WHERE id BETWEEN 980000 AND 989999;

DELETE FROM craft_session WHERE id BETWEEN 980000 AND 989999;
DELETE FROM craft_recommend_event WHERE id BETWEEN 980000 AND 989999 OR user_id BETWEEN 980001 AND 980020;
DELETE FROM craft_recommendation_item WHERE recommendation_id BETWEEN 980000 AND 989999;
DELETE FROM craft_recommendation WHERE id BETWEEN 980000 AND 989999 OR user_id BETWEEN 980001 AND 980020;
DELETE FROM emotion_session WHERE id BETWEEN 980000 AND 989999 OR user_id BETWEEN 980001 AND 980020;

DELETE FROM wellbeing_record WHERE user_id=4 AND record_key='988001';
DELETE FROM user WHERE id BETWEEN 980001 AND 980020;
