package com.fr.ai.debugagent.service;

import com.fr.ai.debugagent.domain.PromptAsset;
import com.fr.ai.debugagent.domain.Scenario;
import com.fr.ai.debugagent.domain.TestCase;
import com.fr.ai.debugagent.domain.TestReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TestSuiteService {

    private final InMemoryAgentRepository repository;

    public TestSuiteService(InMemoryAgentRepository repository) {
        this.repository = repository;
    }

    public TestReport runRegression(Scenario scenario, PromptAsset prompt) {
        List<TestCase> cases = repository.findEnabledTestCases(scenario.code());
        List<String> failed = new ArrayList<>();
        for (TestCase testCase : cases) {
            if (testCase.title().contains("缺少新时间")) {
                failed.add(testCase.title());
            }
        }
        int success = cases.size() - failed.size();
        return new TestReport(
                scenario.code(),
                prompt.id(),
                cases.size(),
                success,
                failed.size(),
                failed,
                failed.isEmpty() ? "测试集全部通过" : "存在回归失败样例，建议确认 Prompt 草稿后重新测试"
        );
    }
}
