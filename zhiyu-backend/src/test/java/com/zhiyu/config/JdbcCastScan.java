package com.zhiyu.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 裸 {@code (Timestamp)} 强转源码扫描器（Java 侧唯一实现）。
 *
 * <p>{@link JdbcCastGuardTest}（全量扫 src/main/java）与 {@link JdbcCastGateConsistencyTest}
 * （样例双跑一致性）共用本类，避免规则出现第三份拷贝。规则与
 * {@code zhiyu-backend/scripts/check-jdbc-timestamp-cast.js}（node 版，lefthook 提交前闸口）
 * 逐字符同源：
 * <ul>
 *   <li>命中：剥离块注释（含跨行 javadoc）与行注释后匹配
 *       {@code \([ \t]*(?:java\.sql\.)?Timestamp[ \t]*\)}——空白只用 ASCII [ \t]，
 *       不用 \s（JS \s 含 Unicode 空白、Java \s 仅 ASCII，字符集不同会漂移）；</li>
 *   <li>豁免：命中行同行注释 {@code // jdbc-cast-ok: <理由≥6字符>}（正则作用于原始行）；</li>
 *   <li>字符串字面量不剥离（两版一致的已知边界，字面量里的 "(Timestamp)" 两边都会命中）。</li>
 * </ul>
 */
final class JdbcCastScan {

    private static final Pattern CAST = Pattern.compile("\\([ \t]*(?:java\\.sql\\.)?Timestamp[ \t]*\\)");
    private static final Pattern EXEMPT = Pattern.compile("jdbc-cast-ok[ \t]*:[ \t]*(\\S.{4,})");

    private JdbcCastScan() {
    }

    /**
     * 扫描一组源码行，返回违规位置（{@code label:行号  代码片段}，行号 1 起）。
     *
     * @param label 输出用位置前缀（相对路径，便于与 node 版输出对比）
     */
    static List<String> findViolations(List<String> lines, String label) {
        List<String> hits = new ArrayList<>();
        boolean inBlock = false;
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            StringBuilder code = new StringBuilder();
            for (int j = 0; j < raw.length(); j++) {
                if (inBlock) {
                    if (raw.charAt(j) == '*' && j + 1 < raw.length() && raw.charAt(j + 1) == '/') {
                        inBlock = false;
                        j++;
                    }
                    continue;
                }
                if (raw.charAt(j) == '/' && j + 1 < raw.length() && raw.charAt(j + 1) == '*') {
                    inBlock = true;
                    j++;
                    continue;
                }
                if (raw.charAt(j) == '/' && j + 1 < raw.length() && raw.charAt(j + 1) == '/') {
                    break;
                }
                code.append(raw.charAt(j));
            }
            if (CAST.matcher(code).find() && !EXEMPT.matcher(raw).find()) {
                hits.add(label + ":" + (i + 1) + "  " + raw.trim());
            }
        }
        return hits;
    }

    /** 豁免行识别（与命中判定同一条 EXEMPT 规则，作用于原始行）；一致性用例核对豁免口径用。 */
    static boolean isExempt(String rawLine) {
        return EXEMPT.matcher(rawLine).find();
    }
}
