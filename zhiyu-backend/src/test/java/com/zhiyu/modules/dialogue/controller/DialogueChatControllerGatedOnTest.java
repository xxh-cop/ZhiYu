package com.zhiyu.modules.dialogue.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** 开关开启时 C1 控制器注册（AA5.2；与 GatedOffTest 同一包，两个独立上下文）。 */
@SpringBootTest(properties = "zhiyu.dialogue.commander=true")
@ActiveProfiles("test")
@DisplayName("调度层开关开启：/api/dialogue/chat 控制器注册")
class DialogueChatControllerGatedOnTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void controllerRegisteredWhenSwitchOn() {
        assertThat(context.getBeanNamesForType(DialogueChatController.class))
                .as("zhiyu.dialogue.commander=true 时控制器应注册")
                .hasSize(1);
    }
}
