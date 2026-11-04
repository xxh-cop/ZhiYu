# BG 批（2026-11-05）：传承人核验通知 + 小组停用/恢复通知
Feature: BG 裁决类动作触达当事人

  # ── BG1：传承人入驻核验 ──

  Scenario: 核验通过与撤销都通知传承人
    Given 一位待认证的传承人（inheritor_profile verified=0）
    When 管理员认证通过（verified=1）
    Then 传承人收到 CAMPUS_AUDIT 通知（refType=inheritor_profile），标题「传承人入驻核验已通过」
    When 管理员之后取消认证（verified=0）
    Then 传承人收到「传承人入驻核验未通过」通知（不同 dedupe 键，正常触达）
    And 同一结果重复触发被 dedupe 去重

  Scenario: 核验通知失败不阻断
    Given 通知存储不可用
    When 管理员认证传承人
    Then 核验状态仍正常更新（只记 warn 日志）

  # ── BG2：小组停用/恢复 ──

  Scenario: 停用与恢复通知组长
    Given 一个有组长的小组（group_member role='组长'）
    When 管理员把小组 status 改为 0（停用）
    Then 组长收到 GROUP_JOIN 通知（refType=group），标题「你的小组已被停用」，正文含小组名
    When 管理员恢复（status=1）
    Then 组长收到「你的小组已恢复使用」通知
    And 全部成员不收到该通知（只触达组长）

  Scenario: 同状态重复操作与小时窗口去重
    Given 小组当前已是停用状态
    When 管理员再次设置为停用
    Then 不产生新通知（状态未变化不打扰）
    When 一小时内重复「恢复→停用→停用」
    Then 停用通知同小时窗口内只推一条
