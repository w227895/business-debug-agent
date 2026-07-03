package com.fr.ai.debugagent.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogRequestHintsTest {

    @Test
    void resolvesOrderKbDevkFromExplicitServiceAndEnvironment() {
        LogRequestHints hints = LogRequestHints.fromUserMessage(
                "traceID 是web_order-deve_snake-a0f0484dc7364435a8a04a15a8b4dab2，然后服务器是order_kb ，devk环境");

        assertThat(hints.primaryKeyword()).isEqualTo("web_order-deve_snake-a0f0484dc7364435a8a04a15a8b4dab2");
        assertThat(hints.profile()).isEqualTo("DEV");
        assertThat(hints.serviceValues()).isEqualTo("order_kb#devk");
        assertThat(hints.toSystemInstruction()).contains("不要根据 traceId 前缀推断服务");
        assertThat(hints.explicitTimeRange()).isFalse();
    }

    @Test
    void resolvesOrderKbDevkFromCompactUserWording() {
        LogRequestHints hints = LogRequestHints.fromUserMessage(
                "web_order-deve_snake-a0f0484dc7364435a8a04a15a8b4dab2 帮我查一下order_devk kb的日志");

        assertThat(hints.primaryKeyword()).isEqualTo("web_order-deve_snake-a0f0484dc7364435a8a04a15a8b4dab2");
        assertThat(hints.profile()).isEqualTo("DEV");
        assertThat(hints.serviceValues()).isEqualTo("order_kb#devk");
    }

    @Test
    void keepsDirectConcreteServiceValue() {
        LogRequestHints hints = LogRequestHints.fromUserMessage(
                "查 order_kb#devk 里的 web_order-deve_snake-a0f0484dc7364435a8a04a15a8b4dab2");

        assertThat(hints.serviceValues()).isEqualTo("order_kb#devk");
    }

    @Test
    void detectsExplicitTimeRange() {
        LogRequestHints hints = LogRequestHints.fromUserMessage(
                "查 order_kb#devk 里的 trace-1，时间 2026-07-03 10:00:00 到 2026-07-03 11:00:00");

        assertThat(hints.explicitTimeRange()).isTrue();
    }
}
