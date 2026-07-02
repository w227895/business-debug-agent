package com.fr.ai.debugagent.api;

import com.fr.ai.debugagent.oms.OmsCookieStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTraceServiceTest {

    @Test
    void extractsTraceIdsFromJsonFieldAndFrTracePattern() {
        OrderTraceService service = new OrderTraceService(new BusinessApiProperties(), new OmsCookieStore());

        assertThat(service.extractTraceIds("""
                {"traceId":"web_order_394298d795814bbb8f197bc48e8a4224.478.17822884429362143"}
                next=agg_deal_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.1.2
                """))
                .containsExactly(
                        "web_order_394298d795814bbb8f197bc48e8a4224.478.17822884429362143",
                        "agg_deal_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.1.2");
    }
}
