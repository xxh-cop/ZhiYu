package com.zhiyu.ai.rag.graph;

import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 有界的非遗关系检索：只使用已审核目录，不调用模型、不递归超过一跳。
 * 每次请求从当前目录重建小图，因此管理端覆盖与来源下架立即生效。
 */
@Component
public class HeritageGraphIndex {

    public static final int MAX_HITS = 6;
    private static final Pattern LATIN = Pattern.compile("[a-z0-9]{2,}");
    private static final Pattern HAN = Pattern.compile("[\\p{IsHan}]{2,}");
    private static final Set<String> STOP = Set.of(
            "什么", "怎么", "如何", "哪些", "可以", "非遗", "了解", "介绍",
            "这个", "那个", "有", "的", "和", "与", "吗", "呢");

    private final HeritageCatalogService catalogs;

    public HeritageGraphIndex(HeritageCatalogService catalogs) {
        this.catalogs = catalogs;
    }

    public List<GraphHit> search(String question) {
        return search(question, catalogs.catalog());
    }

    static List<GraphHit> search(String question, HeritageCatalog catalog) {
        String normalized = normalize(question);
        if (catalog == null || normalized.isBlank()) return List.of();
        Set<String> terms = terms(normalized);
        if (terms.isEmpty()) return List.of();

        List<Node> nodes = nodes(catalog);
        Map<String, Node> byId = new LinkedHashMap<>();
        for (Node node : nodes) byId.put(node.id(), node);

        List<Scored> ranked = nodes.stream()
                .map(node -> new Scored(node, score(normalized, terms, node)))
                .filter(scored -> scored.score() > 0.8)
                .filter(scored -> !"SOURCE".equals(scored.node().type()))
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparingInt(scored -> rank(scored.node().type()))
                        .thenComparing(scored -> scored.node().id()))
                .toList();
        if (ranked.isEmpty()) return List.of();
        double cutoff = Math.max(0.8, ranked.get(0).score() * 0.75);
        List<Scored> seeds = ranked.stream()
                .filter(scored -> scored.score() >= cutoff)
                .limit(3)
                .toList();
        if (seeds.isEmpty()) return List.of();

        LinkedHashMap<String, GraphHit> hits = new LinkedHashMap<>();
        for (Scored seed : seeds) {
            add(hits, seed.node(), seed.node().title(), catalog.version());
        }
        for (Scored seed : seeds) {
            for (Node neighbor : neighbors(seed.node(), catalog, byId, normalized, terms)) {
                if (!"SOURCE".equals(neighbor.type())) continue;
                add(hits, neighbor, relation(seed.node(), neighbor), catalog.version());
                if (hits.size() >= MAX_HITS) return List.copyOf(hits.values());
            }
        }
        for (Scored seed : seeds) {
            for (Node neighbor : neighbors(seed.node(), catalog, byId, normalized, terms)) {
                if ("SOURCE".equals(neighbor.type())) continue;
                add(hits, neighbor, relation(seed.node(), neighbor), catalog.version());
                if (hits.size() >= MAX_HITS) return List.copyOf(hits.values());
            }
        }
        return List.copyOf(hits.values());
    }

    private static String relation(Node seed, Node neighbor) {
        if ("SOURCE".equals(neighbor.type())) {
            return seed.title() + " → 来源：" + neighbor.organization();
        }
        return seed.title() + " → " + neighbor.title();
    }

    private static void add(Map<String, GraphHit> hits, Node node, String relation, String version) {
        if (hits.size() >= MAX_HITS || hits.containsKey(node.id())) return;
        hits.put(node.id(), new GraphHit(node.id(), node.type(), node.title(), node.excerpt(),
                node.url(), node.organization(), node.mediaReuse(), node.sourceIds(), relation, version));
    }

    private static List<Node> neighbors(Node seed, HeritageCatalog catalog, Map<String, Node> byId,
                                        String normalized, Set<String> terms) {
        List<Node> out = new ArrayList<>();
        switch (seed.type()) {
            case "TRADITION" -> {
                seed.sourceIds().stream().map(byId::get).filter(Objects::nonNull).forEach(out::add);
                catalog.patterns().stream()
                        .filter(pattern -> pattern.traditionId().equals(seed.id()))
                        .sorted(Comparator.comparingDouble((HeritageCatalog.Pattern pattern) ->
                                score(normalized, terms, byId.get(pattern.id()))).reversed())
                        .limit(2)
                        .map(pattern -> byId.get(pattern.id()))
                        .forEach(out::add);
            }
            case "PATTERN" -> catalog.patterns().stream().filter(p -> p.id().equals(seed.id())).findFirst()
                    .map(pattern -> {
                        List<Node> linked = new ArrayList<Node>();
                        pattern.sourceIds().stream().map(byId::get).filter(Objects::nonNull)
                                .forEach(linked::add);
                        Node tradition = byId.get(pattern.traditionId());
                        if (tradition != null) linked.add(tradition);
                        return linked;
                    })
                    .ifPresent(out::addAll);
            case "SOURCE" -> {
                catalog.traditions().stream().filter(t -> t.sourceIds().contains(seed.id()))
                        .limit(2).map(t -> byId.get(t.id())).filter(Objects::nonNull).forEach(out::add);
                catalog.patterns().stream().filter(p -> p.sourceIds().contains(seed.id()))
                        .limit(2).map(p -> byId.get(p.id())).filter(Objects::nonNull).forEach(out::add);
            }
            default -> {
            }
        }
        return out;
    }

    private static List<Node> nodes(HeritageCatalog catalog) {
        List<Node> nodes = new ArrayList<>();
        for (HeritageCatalog.Tradition tradition : catalog.traditions()) {
            nodes.add(new Node(tradition.id(), "TRADITION", tradition.name(),
                    String.join(" ", safe(tradition.summary()), safe(tradition.region()),
                            String.join("、", safe(tradition.materials())),
                            String.join("、", safe(tradition.process()))),
                    safe(tradition.summary()), safe(tradition.sourceIds()),
                    null, null, null));
        }
        for (HeritageCatalog.Pattern pattern : catalog.patterns()) {
            nodes.add(new Node(pattern.id(), "PATTERN", pattern.name(),
                    String.join(" ", safe(pattern.designNote()), safe(pattern.learningFocus()),
                            safe(pattern.level())),
                    safe(pattern.learningFocus()), safe(pattern.sourceIds()),
                    null, null, null));
        }
        for (HeritageCatalog.Source source : catalog.sources()) {
            String facts = String.join(" ", safe(source.facts()));
            nodes.add(new Node(source.id(), "SOURCE", source.title(),
                    source.title() + " " + source.organization() + " " + facts,
                    facts.length() > 120 ? facts.substring(0, 120) : facts,
                    List.of(source.id()), source.url(), source.organization(), source.mediaReuse()));
        }
        return nodes;
    }

    private static double score(String normalized, Set<String> terms, Node node) {
        if (node == null) return 0;
        String title = normalize(node.title());
        double score = 0;
        if (!title.isBlank() && normalized.equals(title)) score += 20;
        else if (title.length() >= 2 && (normalized.contains(title) || title.contains(normalized))) score += 10;
        for (String term : terms) {
            if (title.contains(term)) score += 2.5;
        }

        String text = normalize(node.title() + " " + node.text());
        int matched = 0;
        for (String term : terms) {
            if (text.contains(term)) {
                matched++;
            } else if (term.length() == 2
                    && (text.contains(term.substring(0, 1)) || text.contains(term.substring(1)))) {
                score += 0.35;
            }
        }
        return score + 8.0 * matched / Math.max(1, terms.size());
    }

    static Set<String> terms(String normalized) {
        Set<String> terms = new LinkedHashSet<>();
        Matcher latin = LATIN.matcher(normalized);
        while (latin.find()) terms.add(latin.group());
        Matcher han = HAN.matcher(normalized);
        while (han.find()) {
            String run = han.group();
            for (int i = 0; i + 1 < run.length(); i++) {
                String term = run.substring(i, i + 2);
                if (!STOP.contains(term)) terms.add(term);
            }
        }
        return terms;
    }

    private static String normalize(String value) {
        String text = value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        return text.replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static int rank(String type) {
        return switch (type) {
            case "TRADITION" -> 0;
            case "PATTERN" -> 1;
            default -> 2;
        };
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record Node(String id, String type, String title, String text, String excerpt,
                        List<String> sourceIds, String url, String organization, String mediaReuse) {
    }

    private record Scored(Node node, double score) {
    }
}
