package com.fr.ai.debugagent.oms;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpGeneratorTest {

    @Test
    void generateMatchesRfcVectorWhenUsingSixDigits() {
        TotpGenerator generator = new TotpGenerator();

        String code = generator.generate("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Instant.ofEpochSecond(59), 6);

        assertThat(code).isEqualTo("287082");
    }
}
