package com.zhiyu.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 把仓库根 .env 注入为「环境变量式属性源」，使 IntelliJ Run / mvn spring-boot:run 不必手工配环境变量。
 *
 * <p>不用 spring.config.import：.env 是 UPPER_SNAKE 键名，普通 properties 源不做
 * a.b.c ↔ A_B_C 换算，会「文件加载成功但松散命名取不到值」。继承
 * {@link SystemEnvironmentPropertySource} 后与真实 OS 环境变量同构，@Value 与
 * @ConfigurationProperties 都能命中。
 *
 * <p>优先级：addLast → 真实环境变量 > 系统属性 > application*.yml > .env > 默认值。
 * .env 只补位，不覆盖生产注入的真实环境变量。
 *
 * <p>profile 拒绝加载：active profiles 或 spring.profiles.active 文本中出现
 * test（单测用 H2 + sk-placeholder）或 prod / production（防止打包目录误带 dev 假密钥的 .env
 * 被静默用于生产加密）时一律跳过。不做「仅 dev 生效」白名单，避免 staging 等自定义 profile 失效。
 *
 * <p>向上查找只在本仓库内生效：候选目录必须带仓库结构标志（.env.example 或
 * zhiyu-backend/pom.xml），避免误把父目录里无关的 .env 静默加载进来。
 *
 * <p>开关：{@code zhiyu.dotenv.enabled} 可用系统属性（-D）、环境变量、命令行参数或
 * application*.yml 设置；本处理器 order 为 LOWEST_PRECEDENCE，执行时配置数据已加载，
 * 因此 yml 中的同名键同样生效。{@code zhiyu.dotenv.path} 同上，用于显式指定 .env 路径。
 *
 * <p>加载结果在 ContextRefreshedEvent 打印路径与键数量：本方法执行时 logging 尚未初始化，
 * 直接 log 会被丢弃。不打印任何键值。
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);
    private static final int MAX_PARENT_LOOKUP = 5;
    private static final String SOURCE_PREFIX = "zhiyuDotenv:";
    private static final String REPO_MARKER_FILE = ".env.example";
    private static final Path REPO_MARKER_DIR = Path.of("zhiyu-backend");
    private static final String TEST_PROFILE = "test";
    private static final Set<String> PROD_PROFILES = Set.of("prod", "production");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!"true".equalsIgnoreCase(environment.getProperty("zhiyu.dotenv.enabled", "true"))) return;
        String ignoredProfile = ignoredProfile(environment);
        if (TEST_PROFILE.equals(ignoredProfile)) return;
        if (ignoredProfile != null) {
            logLater(application, ignoredProfile);
            return;
        }
        Path file = locate(environment);
        if (file == null) return;
        Map<String, Object> values = parse(file);
        if (values.isEmpty()) return;
        MutablePropertySources sources = environment.getPropertySources();
        String name = SOURCE_PREFIX + file;
        sources.remove(name);
        sources.addLast(new SystemEnvironmentPropertySource(name, values));
        String path = file.toString();
        int count = values.size();
        if (application != null) {
            application.addListeners((ApplicationListener<ContextRefreshedEvent>) event ->
                    log.info("已加载本地 .env 配置：{}（{} 个键，优先级低于真实环境变量）", path, count));
        } else {
            log.info("已加载本地 .env 配置：{}（{} 个键，优先级低于真实环境变量）", path, count);
        }
    }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

    /** 已激活 profile 与 spring.profiles.active 文本合并判定，防 ConfigData 顺序导致漏判。 */
    private String ignoredProfile(ConfigurableEnvironment environment) {
        List<String> declaredProfiles = new ArrayList<>(Arrays.asList(environment.getActiveProfiles()));
        String declared = environment.getProperty("spring.profiles.active");
        if (declared != null) declaredProfiles.addAll(Arrays.asList(declared.split(",")));
        for (String profile : declaredProfiles) {
            String normalized = profile.trim().toLowerCase(Locale.ROOT);
            if (TEST_PROFILE.equals(normalized)) return TEST_PROFILE;
            if (PROD_PROFILES.contains(normalized)) return normalized;
        }
        return null;
    }

    /** 生产 profile 的跳过说明延迟到 ContextRefreshedEvent，否则会被 logging 初始化前的静默期吞掉。 */
    private void logLater(SpringApplication application, String profile) {
        if (application != null) {
            application.addListeners((ApplicationListener<ContextRefreshedEvent>) event ->
                    log.info("检测到生产 profile（{}），跳过 .env 加载", profile));
        } else {
            log.info("检测到生产 profile（{}），跳过 .env 加载", profile);
        }
    }

    /** 显式路径 > 自 user.dir 向上查找（IntelliJ 工作目录通常是 zhiyu-backend/，脚本在仓库根）。 */
    private Path locate(ConfigurableEnvironment environment) {
        String override = environment.getProperty("zhiyu.dotenv.path");
        if (override == null || override.isBlank()) override = System.getenv("ZHIYU_DOTENV_PATH");
        if (override != null && !override.isBlank()) {
            Path p = Path.of(override).toAbsolutePath().normalize();
            return Files.isRegularFile(p) ? p : null;
        }
        Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int i = 0; i <= MAX_PARENT_LOOKUP && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate) && isRepoRoot(dir)) return candidate;
        }
        return null;
    }

    /** 只有 .env 一个文件不算本仓库；必须看到结构性标志。 */
    private boolean isRepoRoot(Path dir) {
        return Files.isRegularFile(dir.resolve(REPO_MARKER_FILE))
                || Files.isRegularFile(dir.resolve(REPO_MARKER_DIR).resolve("pom.xml"));
    }

    /** 与 start-backend.ps1 同语义：跳过空行/注释，首个 = 分割，成对引号去壳；任何解析异常都不抛出。 */
    private Map<String, Object> parse(Path file) {
        Map<String, Object> map = new LinkedHashMap<>();
        try {
            for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int idx = line.indexOf('=');
                if (idx < 1) continue;
                String key = line.substring(0, idx).trim();
                if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) continue;
                String value = line.substring(idx + 1).trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                map.put(key, value);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("读取 {} 失败，本次跳过 .env 加载：{}", file, e.toString());
            return Map.of();
        }
        return map;
    }
}
