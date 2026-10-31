package com.zhiyu.ai.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "zhiyu.speech.provider=qianwen",
        "QIANWEN_SPEECH_ENABLED=true",
        "QIANWEN_API_KEY=test-only-key"
})
class QianwenSpeechContextTest {

    @Autowired
    private SpeechClient speechClient;

    @Test
    void qianwenProviderReplacesGatewayBean() {
        assertInstanceOf(QianwenSpeechClient.class, speechClient);
        assertTrue(speechClient.available());
    }
}
