package com.fr.ai.debugagent.findlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fr.ai.debugagent.oms.TotpGenerator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class FindLogServiceTest {

    private final FindLogService service = new FindLogService(
            new FindLogProperties(),
            new TotpGenerator(),
            new ObjectMapper().findAndRegisterModules());

    @Test
    void parsesConcreteServiceValues() {
        assertThat(service.parseServices("order_deve#deve, account_deve#deve qim_deve#deve"))
                .containsExactly("order_deve#deve", "account_deve#deve", "qim_deve#deve");
    }

    @Test
    void defaultsToPlainGrepWithoutContextLines() {
        assertThat(service.contextOption(null)).isEmpty();
        assertThat(service.contextOption("")).isEmpty();
        assertThat(service.contextOption("bad")).isEmpty();
        assertThat(service.contextOption("80")).isEqualTo("-C 80");
    }

    @Test
    void defaultStartDateUsesLastTwelveHours() {
        LocalDateTime expected = LocalDateTime.now().minusHours(12);
        LocalDateTime actual = LocalDateTime.parse(service.defaultStartDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        assertThat(Math.abs(Duration.between(expected, actual).toSeconds())).isLessThanOrEqualTo(2);
    }

    @Test
    void extractsExcerptsFromSseDataResultArray() {
        var excerpts = service.extractExcerptsFromSseData("""
                {
                  "costTime": 2,
                  "ip": "172.16.16.7",
                  "result": [
                    {"command": "grep", "result": ["line 1 trace", "line 2 trace"]}
                  ]
                }
                """);

        assertThat(excerpts).hasSize(1);
        assertThat(excerpts.get(0))
                .contains("line 1 trace")
                .contains("line 2 trace");
    }
}
