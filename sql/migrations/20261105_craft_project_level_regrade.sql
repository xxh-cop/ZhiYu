-- 手作项目难度重分级（2026-11-05）
-- 依据：docs/craft-level-grading-20261105.md
-- 判级三维度：估时 / 工序可逆性 / 认知与动作负荷；工具风险不参与判级（craft_safety.level 独立承载 LOW/GUIDED/HIGH）。
-- 旧值 → 新值：
--   101   解压扎染方巾   1 → 2（估时 45min；浸染定型半不可逆）
--   102   赣绣小书签     1 → 2（估时 60min；穿针藏线头需持续精细控制）
--   103   剪纸释压团花   1 → 2（剪断连接处需重来，容错窗口窄）
--   940004 双色纸编书签  1 → 2（9 条双色纸条多行交错、起始方向相反，规则追踪负荷高；多材料复合）
-- 104 陶艺捏塑杯垫维持 2（缺步骤/材料数据，待内容补齐后复核；若确认含烧制环节应升 3）。
-- 其余种子项目维持 1；L3 空缺是标准一致的结果，不为凑分布拔高。
-- 幂等：仅当当前值不符时才更新；重复执行影响 0 行、结果一致。
UPDATE craft_project SET level = 2 WHERE id = 101    AND level <> 2;
UPDATE craft_project SET level = 2 WHERE id = 102    AND level <> 2;
UPDATE craft_project SET level = 2 WHERE id = 103    AND level <> 2;
UPDATE craft_project SET level = 2 WHERE id = 940004 AND level <> 2;
