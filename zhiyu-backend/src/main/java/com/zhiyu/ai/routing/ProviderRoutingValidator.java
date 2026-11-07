package com.zhiyu.ai.routing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * provider 路由策略启动校验（2026-11-08 装配层批，D4 第二层防线）。
 *
 * <p>目标：把「审核建议走本地」的打开成本抬高到<b>必须改代码走评审</b>——
 * 配置层不给 moderation 本地键（第一层，见 {@link ModelRouter#CLOUD_ONLY_TASKS}），
 * 本类在启动期对装配完成的策略表再做一次 fail-fast 复核（第二层），
 * 契约测试钉死（第三层）。
 *
 * <p>校验内容：
 * <ol>
 *   <li>CLOUD_ONLY 集合（含 moderation）任何任务的 fallbackAllowed 为 true →
 *       抛出 {@link IllegalStateException}，启动失败；</li>
 *   <li>影子判定双开关（D8）只开一个 → 不生效（不阻断启动），记 WARN 防误以为已开。</li>
 * </ol>
 */
@Slf4j
@Component
public class ProviderRoutingValidator implements SmartInitializingSingleton {

    private final ModelRouter router;

    public ProviderRoutingValidator(ModelRouter router) {
        this.router = router;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String task : ModelRouter.CLOUD_ONLY_TASKS) {
            if (router.fallbackAllowed(task)) {
                throw new IllegalStateException(
                        "provider 路由误配：cloud-only 能力被指向本地兜底 task=" + task
                                + "。审核建议与相关能力按 D1/D4 决策不允许本地顶替；"
                                + "如确需变更，必须修改代码并走评审，不接受配置打开。");
            }
        }
        if (!router.shadowLocalEffective()) {
            // 只开一个的场景值得点名：运维可能以为演示本地影子已生效
            log.info("影子判定本地演示未生效（双开关需同时成立：local-demo-mode 且 "
                    + "shadow.demo-providers 含 ollama）。当前按生产口径（云端）执行。");
        }
    }
}
