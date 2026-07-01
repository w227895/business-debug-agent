package com.fr.ai.debugagent.service;

import com.fr.ai.debugagent.agent.AgentDebugRequest;
import com.fr.ai.debugagent.domain.FewShotSuggestion;
import com.fr.ai.debugagent.domain.JsonValidationResult;
import com.fr.ai.debugagent.domain.ParseResult;
import com.fr.ai.debugagent.domain.PromptAsset;
import com.fr.ai.debugagent.domain.PromptSuggestion;
import com.fr.ai.debugagent.domain.Scenario;
import org.springframework.stereotype.Service;

@Service
public class FailureAnalysisService {

    public String analyze(
            AgentDebugRequest request,
            Scenario scenario,
            ParseResult parseResult,
            JsonValidationResult validation
    ) {
        if (validation.valid()) {
            return "当前 JSON 结构校验通过。若业务仍认为结果错误，应继续对比字段值和标准答案。";
        }
        if (validation.missingFields().contains("newDepartureTime")) {
            return "主要问题是缺失 newDepartureTime。通常说明 Prompt 对时间提取规则不够明确，或 Few-shot 未覆盖该邮件格式。";
        }
        return "模型输出未满足目标 JSON 结构，建议优先补齐缺失字段规则，并新增对应 Few-shot。";
    }

    public PromptSuggestion suggestPromptChange(PromptAsset prompt, JsonValidationResult validation) {
        if (validation.valid()) {
            return new PromptSuggestion(
                    "无需立即修改 Prompt",
                    "结构校验已通过",
                    "继续对比字段值准确性后再决定是否调整 Prompt。",
                    ""
            );
        }
        return new PromptSuggestion(
                "补充航变关键字段提取规则",
                "当前输出缺失必填字段：" + String.join("、", validation.missingFields()),
                "当邮件出现新航班时间、调整至、变更为、改至等描述时，必须提取 newDepartureTime；多航段时优先提取发生变更的航段。",
                "字段提取要求：必须输出 orderNo、changeType、originalFlightNo、newFlightNo、newDepartureTime。若邮件存在多航段，优先选择发生航变的航段。"
        );
    }

    public FewShotSuggestion suggestFewShot(
            Scenario scenario,
            AgentDebugRequest request,
            ParseResult parseResult,
            JsonValidationResult validation
    ) {
        String expected = request.expectedJson() == null || request.expectedJson().isBlank()
                ? parseResult.outputJson()
                : request.expectedJson();
        return new FewShotSuggestion(
                scenario.name() + "失败样例补充",
                validation.valid() ? "结构校验通过，可作为边界样例沉淀。" : "当前样例触发缺失字段，适合补充为 Few-shot。",
                request.businessText(),
                expected
        );
    }
}
