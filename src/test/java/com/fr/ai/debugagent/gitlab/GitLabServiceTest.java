package com.fr.ai.debugagent.gitlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitLabServiceTest {

    @Test
    void extractsUsefulKeywordsFromStackTraceAndPath() {
        GitLabService service = new GitLabService(new GitLabProperties(), new ObjectMapper());

        List<String> keywords = service.extractKeywords("""
                java.lang.IllegalStateException: bad order status
                    at com.fr.order.controller.OrderController.getStatus(OrderController.java:42)
                    at com.fr.order.service.OrderStatusService.check(OrderStatusService.java:88)
                POST /order/geAllStatusOrdertLogs.do parentId=17428182283024
                """);

        assertThat(keywords)
                .contains("OrderController", "getStatus", "OrderStatusService", "check")
                .contains("/order/geAllStatusOrdertLogs.do", "geAllStatusOrdertLogs.do");
    }

    @Test
    void failsFastWhenTokenIsMissing() {
        GitLabProperties properties = new GitLabProperties();
        properties.getProjects().put("order", "group/order");
        GitLabService service = new GitLabService(properties, new ObjectMapper());

        GitLabCodeLookupResult result = service.findCodeByLog(
                "at com.fr.order.controller.OrderController.getStatus(OrderController.java:42)",
                "order",
                "master");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("GitLab token 未配置");
        assertThat(result.keywords()).contains("OrderController", "getStatus");
    }

    @Test
    void infersProjectSearchKeywordsFromLogText() {
        GitLabService service = new GitLabService(new GitLabProperties(), new ObjectMapper());

        List<String> keywords = service.inferProjectSearchKeywords("""
                web_order-deve_snake-7033111d0279488da8bc4bf4f9918877
                at com.fr.order.controller.OrderController.getStatus(OrderController.java:42)
                POST /order/geAllStatusOrdertLogs.do
                """, "");

        assertThat(keywords).contains("order");
    }

    @Test
    void serviceHintIsEnoughForProjectSearch() {
        GitLabService service = new GitLabService(new GitLabProperties(), new ObjectMapper());

        assertThat(service.inferProjectSearchKeywords("plain exception log", "qim"))
                .containsExactly("qim");
    }
}
