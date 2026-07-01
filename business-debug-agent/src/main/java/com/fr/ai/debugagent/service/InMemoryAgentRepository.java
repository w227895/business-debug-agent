package com.fr.ai.debugagent.service;

import com.fr.ai.debugagent.domain.FewShotExample;
import com.fr.ai.debugagent.domain.OutputSchema;
import com.fr.ai.debugagent.domain.PromptAsset;
import com.fr.ai.debugagent.domain.TestCase;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class InMemoryAgentRepository {

    private static final String FLIGHT_PROMPT_ID = "prompt-flight-change-v1";

    public PromptAsset findPublishedPrompt(String scenarioCode) {
        if (!"flight_change_mail".equals(scenarioCode)) {
            return new PromptAsset(
                    "prompt-aftersale-v1",
                    "aftersale_mail",
                    "售后邮件解析 Prompt",
                    "v0.1",
                    "你是售后邮件解析助手，负责把邮件解析成售后接口 JSON。",
                    "请解析以下售后邮件：{{input}}",
                    "published"
            );
        }
        return new PromptAsset(
                FLIGHT_PROMPT_ID,
                "flight_change_mail",
                "航变邮件解析 Prompt",
                "v0.1",
                "你是航变邮件解析助手，负责把航司邮件解析成航变接口入参 JSON。只输出 JSON。",
                "请从邮件中提取订单号、原航班号、新航班号、新起飞时间、航变类型：{{input}}",
                "published"
        );
    }

    public List<FewShotExample> findFewShots(String promptId) {
        if (!FLIGHT_PROMPT_ID.equals(promptId)) {
            return List.of();
        }
        return List.of(
                new FewShotExample(
                        "fs-flight-001",
                        promptId,
                        "单航段起飞时间变更",
                        "航司通知：订单 FR123，原航班 MU5101 变更为 MU5101，起飞时间调整至 2026-07-01 09:30。",
                        """
                                {"orderNo":"FR123","changeType":"TIME_CHANGE","originalFlightNo":"MU5101","newFlightNo":"MU5101","newDepartureTime":"2026-07-01 09:30"}
                                """,
                        true
                )
        );
    }

    public OutputSchema findOutputSchema(String promptId) {
        return new OutputSchema(
                "schema-flight-change-v1",
                promptId,
                "航变接口入参 JSON",
                List.of("orderNo", "changeType", "originalFlightNo", "newFlightNo", "newDepartureTime"),
                Map.of(
                        "orderNo", "string",
                        "changeType", "string",
                        "originalFlightNo", "string",
                        "newFlightNo", "string",
                        "newDepartureTime", "string"
                ),
                """
                        {"orderNo":"FR123","changeType":"TIME_CHANGE","originalFlightNo":"MU5101","newFlightNo":"MU5101","newDepartureTime":"2026-07-01 09:30"}
                        """
        );
    }

    public List<TestCase> findEnabledTestCases(String scenarioCode) {
        return List.of(
                new TestCase(
                        "tc-flight-001",
                        "flight_change_mail",
                        "单航段时间变更",
                        "订单 FR123 原航班 MU5101 起飞时间变更至 2026-07-01 09:30。",
                        """
                                {"orderNo":"FR123","changeType":"TIME_CHANGE","originalFlightNo":"MU5101","newFlightNo":"MU5101","newDepartureTime":"2026-07-01 09:30"}
                                """,
                        true
                ),
                new TestCase(
                        "tc-flight-002",
                        "flight_change_mail",
                        "多航段缺少新时间样例",
                        "订单 FR456，第一段 MU5101 正常，第二段 CA8888 航班时间变更，请关注新航班 CA8888。",
                        """
                                {"orderNo":"FR456","changeType":"TIME_CHANGE","originalFlightNo":"CA8888","newFlightNo":"CA8888","newDepartureTime":"2026-07-02 10:00"}
                                """,
                        true
                )
        );
    }
}
