package com.fr.ai.debugagent.agent;

import com.fr.ai.debugagent.domain.FewShotSuggestion;
import com.fr.ai.debugagent.domain.JsonValidationResult;
import com.fr.ai.debugagent.domain.ParseResult;
import com.fr.ai.debugagent.domain.PromptAsset;
import com.fr.ai.debugagent.domain.PromptSuggestion;
import com.fr.ai.debugagent.domain.Scenario;
import com.fr.ai.debugagent.domain.TestReport;
import com.fr.ai.debugagent.service.FailureAnalysisService;
import com.fr.ai.debugagent.service.InMemoryAgentRepository;
import com.fr.ai.debugagent.service.JsonValidationService;
import com.fr.ai.debugagent.service.ParseService;
import com.fr.ai.debugagent.service.ScenarioIdentifier;
import com.fr.ai.debugagent.service.TestSuiteService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentOrchestrator {

    private final ScenarioIdentifier scenarioIdentifier;
    private final InMemoryAgentRepository repository;
    private final ParseService parseService;
    private final JsonValidationService jsonValidationService;
    private final FailureAnalysisService failureAnalysisService;
    private final TestSuiteService testSuiteService;

    public AgentOrchestrator(
            ScenarioIdentifier scenarioIdentifier,
            InMemoryAgentRepository repository,
            ParseService parseService,
            JsonValidationService jsonValidationService,
            FailureAnalysisService failureAnalysisService,
            TestSuiteService testSuiteService
    ) {
        this.scenarioIdentifier = scenarioIdentifier;
        this.repository = repository;
        this.parseService = parseService;
        this.jsonValidationService = jsonValidationService;
        this.failureAnalysisService = failureAnalysisService;
        this.testSuiteService = testSuiteService;
    }

    public AgentDebugResponse debug(AgentDebugRequest request) {
        List<String> trace = new ArrayList<>();

        Scenario scenario = scenarioIdentifier.identify(request.userMessage(), request.businessText());
        trace.add("identifyScenario: " + scenario.name());

        PromptAsset prompt = repository.findPublishedPrompt(scenario.code());
        trace.add("getCurrentPrompt: " + prompt.name() + " " + prompt.version());

        trace.add("getFewShots: " + repository.findFewShots(prompt.id()).size());
        trace.add("getOutputSchema: " + repository.findOutputSchema(prompt.id()).name());

        ParseResult parseResult = parseService.parse(scenario, prompt, request.businessText());
        trace.add("runParse: " + parseResult.provider());

        JsonValidationResult validation = jsonValidationService.validate(prompt.id(), parseResult.outputJson());
        trace.add("validateJson: " + (validation.valid() ? "valid" : "invalid"));

        String failureAnalysis = failureAnalysisService.analyze(request, scenario, parseResult, validation);
        trace.add("analyzeFailure: completed");

        PromptSuggestion promptSuggestion = failureAnalysisService.suggestPromptChange(prompt, validation);
        trace.add("suggestPromptChange: draft");

        FewShotSuggestion fewShotSuggestion = failureAnalysisService.suggestFewShot(scenario, request, parseResult, validation);
        trace.add("suggestFewShot: draft");

        TestReport report = testSuiteService.runRegression(scenario, prompt);
        trace.add("runTestSuite: " + report.successCount() + "/" + report.totalCount());

        String conclusion = buildConclusion(validation, report);
        trace.add("generateReport: completed");

        return new AgentDebugResponse(
                scenario,
                parseResult,
                validation,
                failureAnalysis,
                promptSuggestion,
                fewShotSuggestion,
                report,
                trace,
                conclusion
        );
    }

    private String buildConclusion(JsonValidationResult validation, TestReport report) {
        if (validation.valid()) {
            return "本次解析结果 JSON 结构合法，未发现必填字段缺失。建议结合字段值和业务预期继续确认。"
                    + " 当前测试集通过率为 " + report.successRateText() + "。";
        }
        return "本次解析结果存在结构或字段问题，优先处理缺失字段："
                + String.join("、", validation.missingFields())
                + "。当前测试集通过率为 " + report.successRateText() + "，建议先保存草稿并回归测试。";
    }
}
