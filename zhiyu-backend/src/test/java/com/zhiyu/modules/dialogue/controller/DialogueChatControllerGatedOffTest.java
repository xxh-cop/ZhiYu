package com.zhiyu.modules.dialogue.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 开关关闭（默认）时 C1 控制器不注册（AA5.2）。
 *
 * <p>未注册路径对已认证用户返回既有 1004 语义——不注册一个「总是报错的假端点」。
 * 同 profile 下 EmotionController 走原路径（由 EmotionControllerTest 覆盖，调用链不变）。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("调度层开关关闭：/api/dialogue/chat 控制器未注册")
class DialogueChatControllerGatedOffTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void controllerNotRegisteredWhenSwitchOff() {
        assertThat(context.getBeanNamesForType(DialogueChatController.class))
                .as("zhiyu.dialogue.commander=false（默认）时不得注册控制器")
                .isEmpty();
        // 调度服务本身始终注册（仅控制器受开关约束）
        assertThat(context.getBeanNamesForType(
                com.zhiyu.modules.dialogue.DialogueCommander.class)).hasSize(1);
    }
}
