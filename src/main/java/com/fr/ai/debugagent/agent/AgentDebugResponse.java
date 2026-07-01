package com.fr.ai.debugagent.agent;

import com.fr.ai.debugagent.domain.FewShotSuggestion;
import com.fr.ai.debugagent.domain.JsonValidationResult;
import com.fr.ai.debugagent.domain.ParseResult;
import com.fr.ai.debugagent.domain.PromptSuggestion;
import com.fr.ai.debugagent.domain.Scenario;
import com.fr.ai.debugagent.domain.TestReport;

import java.util.List;

public record AgentDebugResponse(
        Scenario scenario,
        ParseResult parseResult,
        JsonValidationResult validationResult,
        String failureAnalysis,
        PromptSuggestion promptSuggestion,
        FewShotSuggestion fewShotSuggestion,
        TestReport testReport,
        List<String> toolTrace,
        String conclusion
) {
}
