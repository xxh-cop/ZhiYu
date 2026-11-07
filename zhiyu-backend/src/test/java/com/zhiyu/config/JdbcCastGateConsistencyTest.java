package com.zhiyu.config;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 双闸口一致性用例：同一组样例分别喂给 Java 版（{@link JdbcCastScan}）与 node 版
 * （{@code scripts/check-jdbc-timestamp-cast.js}），断言两版逐文件结论完全相同——
 * 防两套实现规则漂移（「提交能过、构建失败」或反过来的排查成本）。
 *
 * <p>样例覆盖五种形态：裸强转、行注释里的强转、跨行块注释里的强转、
 * 带豁免注解的强转、字符串字面量里的 "(Timestamp)"（两版一致的已知误报边界）。
 * 除双跑对比外，同时钉住每种形态的预期结论（裸强转=命中、注释内=不命中、
 * 豁免=不命中、字面量=命中），保证规则语义本身不劣化。
 *
 * <p>样例落在 {@code target/jdbc-gate-consistency/}（构建产物目录，不进版本库）。
 * node 不可用时整组跳过（CI backend job 无 node——Java 闸口在 CI 照常生效，
 * 一致性校验在开发机每次 mvn test 时执行）。
 */
class JdbcCastGateConsistencyTest {

    private static final Path SCRIPT = Path.of("scripts", "check-jdbc-timestamp-cast.js");
    private static final Path SAMPLES = Path.of("target", "jdbc-gate-consistency");
    /** node 输出行的命中格式：两个空格 + 相对路径:行号（豁免行含「豁免理由」，另行剔除）。 */
    private static final Pattern NODE_LINE = Pattern.compile("^\\s{2}(\\S+\\.java):(\\d+)");

    /** 样例：文件名 → 源码文本。 */
    private static final List<String[]> SAMPLE_FILES = List.of(
            new String[]{"Bare.java", """
                    package t;
                    class Bare {
                        Object f(Object v) {
                            return ((java.sql.Timestamp) v);
                        }
                    }
                    """},
            new String[]{"LineComment.java", """
                    package t;
                    class LineComment {
                        // 历史写法：return ((Timestamp) v); —— 注释里的强转不算命中
                        Object f(Object v) {
                            return v;
                        }
                    }
                    """},
            new String[]{"BlockComment.java", """
                    package t;
                    /**
                     * 旧实现：LocalDateTime t = ((Timestamp) row.get("create_time")).toLocalDateTime();
                     * 跨行再补一刀：((java.sql.Timestamp) v)
                     */
                    class BlockComment {
                        Object f(Object v) {
                            return v;
                        }
                    }
                    """},
            new String[]{"Exempt.java", """
                    package t;
                    class Exempt {
                        Object f(Object v) {
                            return ((Timestamp) v); // jdbc-cast-ok: 驱动矩阵已确认本调用点只有 H2
                        }
                    }
                    """},
            new String[]{"StringLiteral.java", """
                    package t;
                    class StringLiteral {
                        static final String MSG = "禁止裸 (Timestamp) 强转";
                    }
                    """}
    );

    /** 每个样例的预期结论（命中=在两版输出中都应出现）。 */
    private static final Set<String> EXPECTED_HITS = Set.of("Bare.java:4", "StringLiteral.java:3");

    @BeforeAll
    static void writeSamples() throws IOException {
        deleteRecursively(SAMPLES);
        for (String[] sample : SAMPLE_FILES) {
            Path dir = SAMPLES.resolve(sample[0].replace(".java", "")).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Files.write(dir.resolve(sample[0]),
                    List.of(sample[1].split("\n")), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("同一组样例双跑：Java 版与 node 版逐文件结论完全一致（含豁免与字面量边界）")
    void bothGatesAgreeOnAllSamples() throws Exception {
        Assumptions.assumeTrue(nodeAvailable(), "本机无 node，跳过双跑一致性（CI backend job 无 node；Java 闸口照常生效）");

        // ── Java 版结论（真实实现 JdbcCastScan，逐样例单文件喂入） ──
        Set<String> javaHits = new TreeSet<>();
        for (String[] sample : SAMPLE_FILES) {
            String fileName = sample[0];
            Path file = SAMPLES.resolve(fileName.replace(".java", "")).resolve(fileName);
            for (String hit : JdbcCastScan.findViolations(
                    Files.readAllLines(file, StandardCharsets.UTF_8), fileName)) {
                javaHits.add(hit.substring(0, hit.indexOf("  ")).trim());
            }
        }

        // ── node 版结论（真实脚本，扫同一组样例目录） ──
        Set<String> nodeHits = new TreeSet<>();
        List<String> nodeOutput = new ArrayList<>();
        int nodeExit = runNode(nodeOutput);
        for (String line : nodeOutput) {
            if (line.contains("豁免理由")) {
                continue;
            }
            var m = NODE_LINE.matcher(line);
            if (m.find()) {
                String rel = m.group(1).replace('\\', '/');
                String fileName = rel.substring(rel.lastIndexOf('/') + 1);
                nodeHits.add(fileName + ":" + m.group(2));
            }
        }

        // ── 一致性：两版命中集合完全相等 ──
        assertEquals(javaHits, nodeHits,
                "Java 版与 node 版结论不一致（规则漂移）: java=" + javaHits + " node=" + nodeHits);
        // ── 语义钉桩：每种形态的预期结论（裸强转/字面量=命中；注释内/豁免=不命中） ──
        assertEquals(EXPECTED_HITS, javaHits, "Java 版对样例形态的预期结论变化: " + javaHits);
        // ── 退出码口径：有命中 node 必须以 1 退出 ──
        assertEquals(javaHits.isEmpty() ? 0 : 1, nodeExit, "node 退出码与命中集合不符");
        // ── 豁免口径：Exempt.java 在两版都不计入命中，且 node 版逐行列示了豁免原因 ──
        assertTrue(nodeOutput.stream().anyMatch(l -> l.contains("Exempt.java") && l.contains("豁免理由")),
                "node 版未列示豁免行（豁免不应静默）: " + nodeOutput);
    }

    private static boolean nodeAvailable() {
        try {
            return new ProcessBuilder("node", "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static int runNode(List<String> outputLines) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("node",
                SCRIPT.toAbsolutePath().toString(), SAMPLES.toAbsolutePath().toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        outputLines.addAll(out.lines().collect(Collectors.toList()));
        return p.waitFor();
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
