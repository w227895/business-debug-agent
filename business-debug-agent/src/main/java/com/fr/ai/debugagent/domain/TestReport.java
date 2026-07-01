package com.fr.ai.debugagent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record TestReport(
        String scenarioCode,
        String promptId,
        int totalCount,
        int successCount,
        int failCount,
        List<String> failedCaseTitles,
        String summary
) {

    @JsonProperty("successRateText")
    public String successRateText() {
        if (totalCount == 0) {
            return "0.00%";
        }
        BigDecimal rate = BigDecimal.valueOf(successCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);
        return rate + "%";
    }
}