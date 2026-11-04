package com.zhiyu.modules.crisis;

import java.util.regex.Pattern;

/**
 * 危机留存脱敏器（AJ，2026-11-04）。
 *
 * <p>危机摘要规则上不含用户原文（见 {@code CrisisSessionLogService} 的模板式概括），
 * 本类是<b>防御性第二道</b>：任何进入留存文本的内容先过一遍可识别信息替换。
 * 规则（全部替换为占位符，不可逆）：
 * <ul>
 *   <li>手机号：1[3-9] 开头 11 位 → [手机号]</li>
 *   <li>身份证号：18 位（末位可 X）→ [身份证号]</li>
 *   <li>姓名线索：「我叫/我是/名叫/名字是/他叫/她叫 + 2~4 汉字」→ 前缀+[姓名]</li>
 *   <li>学校/班级：2~10 汉字 + 大学/学院/中学/小学/学校 → [学校]；数字+班 → [班级]</li>
 *   <li>详细住址：汉字 2~8 + 路/街/巷/弄 → [路名]；数字+栋/幢/单元/室/号 → [住址编号]</li>
 * </ul>
 * 纯函数、无状态，可单测。
 */
public final class CrisisDesensitizer {

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");
    private static final Pattern NAME_CLUE = Pattern.compile(
            "(我叫|我是|名叫|名字是|他叫|她叫)[\\p{IsHan}]{2,4}");
    private static final Pattern SCHOOL = Pattern.compile(
            "[\\p{IsHan}]{2,10}(大学|学院|中学|小学|学校)");
    private static final Pattern CLAZZ = Pattern.compile("(?<!\\d)\\d{1,3}\\s*班");
    private static final Pattern ROAD = Pattern.compile(
            "[\\p{IsHan}]{2,8}(路|街|巷|弄)");
    private static final Pattern ADDRESS_NO = Pattern.compile("\\d+\\s*(栋|幢|单元|室|号)");

    private CrisisDesensitizer() {
    }

    /** 返回脱敏后的文本（null/空白原样返回）。 */
    public static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String r = ID_CARD.matcher(text).replaceAll("[身份证号]");
        r = PHONE.matcher(r).replaceAll("[手机号]");
        r = NAME_CLUE.matcher(r).replaceAll(m -> m.group(1) + "[姓名]");
        r = SCHOOL.matcher(r).replaceAll("[学校]");
        r = CLAZZ.matcher(r).replaceAll("[班级]");
        r = ROAD.matcher(r).replaceAll("[路名]");
        r = ADDRESS_NO.matcher(r).replaceAll("[住址编号]");
        return r;
    }
}
