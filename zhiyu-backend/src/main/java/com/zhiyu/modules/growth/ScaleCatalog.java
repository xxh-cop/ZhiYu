package com.zhiyu.modules.growth;

import com.zhiyu.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/** Fixed questionnaires; no LLM inference or diagnostic labels are used in scoring. */
@Component
public class ScaleCatalog {
    public record Scale(String id, String title, String version, String period, List<String> questions,
                        List<String> options, String source, String attribution) {}
    private static final List<String> OPTIONS = List.of("完全没有", "几天", "超过一半的天数", "几乎每天");
    private static final String CREDIT = "由 Spitzer、Williams、Kroenke 等开发，Pfizer 教育资助；原量表允许复制、翻译和分发。中文题面由来源繁体字转简体；仅供自我了解。";
    private final List<Scale> scales = List.of(
        new Scale("PHQ9", "近期感受回顾 · PHQ-9", "zh-Hans-from-zh-Hant-2026.10", "过去两周", List.of(
            "对做事没有什么兴趣或乐趣", "情绪低落、沮丧或绝望", "难以入睡或保持睡眠状态，或睡眠过多",
            "感觉疲倦或精力不足", "食欲不振或暴饮暴食", "自我感觉不好，或者觉得自己很失败，或者让自己或家人失望",
            "难以集中精力做事，例如看报纸或看电视", "移动或说话如此缓慢以至于其他人可能会注意到？或者相反，非常烦躁或不安，以至于你比平时走动得更多",
            "认为自己死了会更好，或者想以某种方式伤害自己"), OPTIONS,
            "https://www.sbirtoregon.org/wp-content/uploads/PHQ-9-chinese-traditional-pdf.pdf", CREDIT),
        new Scale("GAD7", "近期担忧回顾 · GAD-7", "zh-Hans-from-zh-Hant-2026.10", "过去两周", List.of(
            "感到紧张、焦虑或紧张", "无法停止或控制担忧", "太担心不同的事情", "放松困难", "焦躁不安，难以静坐",
            "变得容易烦恼或烦躁", "感到害怕，好像可能会发生可怕的事情"), OPTIONS,
            "https://www.sbirtoregon.org/wp-content/uploads/GAD-7-chinese-pdf.pdf", CREDIT)
    );
    public List<Scale> available() { return scales; }
    public Scale require(String id, String version) {
        return scales.stream().filter(s -> s.id().equals(id) && s.version().equals(version)).findFirst()
            .orElseThrow(() -> new BusinessException(1001, "量表版本不可用，请重新进入"));
    }
    public Map<String,Object> score(Scale scale, List<Integer> answers, boolean draft) {
        if (answers.size()!=scale.questions().size() || answers.stream().anyMatch(n -> n==null || n<(draft?-1:0) || n>3))
            throw new BusinessException(1001,"请按题目完整作答，选项范围为 0 至 3");
        boolean crisis=scale.id().equals("PHQ9") && answers.get(8)>0;
        int score=answers.stream().filter(n->n>=0).mapToInt(Integer::intValue).sum();
        String interval=score<5?"0–4":score<10?"5–9":score<15?"10–14":scale.id().equals("GAD7")?"15–21":score<20?"15–19":"20–27";
        String message=score<5?"本次报告的困扰较少，仍可关注日常感受。":score<10?"本次报告了一些困扰，可以留意它们对日常生活的影响。":"建议与专业心理咨询人员讨论近期感受，获得进一步支持。";
        if(crisis) message="先照顾此刻的安全，请前往心理援助页面获得支持。";
        return Map.of("score",score,"interval",interval,"message",message,"crisis",crisis,"diagnostic",false);
    }
}
