package com.zhiyu.modules.growth;

import java.util.List;

/** 工作坊材料包：按活动模板与非遗工艺整理的准备清单，不含采购链接和价格。 */
public record MaterialKitCatalog(String version, String disclaimer, List<MaterialKit> kits) {

    public record MaterialKit(String id, String name, String workshopTemplateId, String traditionId,
                              String audience, int capacity, List<Item> items, List<String> tools,
                              List<String> preparation, List<String> safety,
                              String digitalAlternative, String sourceNote) {
    }

    public record Item(String name, String quantity, String note) {
    }
}
