package com.julio.lifeorganizer.common.logging;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class RequestIdFilterTest {

    @Test
    void doFilter_putsRequestIdInMdcAndClearsAfterChainCompletes() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/anything");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // Capture the MDC value seen DURING the chain.
        String[] insideChain = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) {
                insideChain[0] = MDC.get(RequestIdFilter.MDC_KEY);
            }
        };

        filter.doFilter(req, res, chain);

        assertThat(insideChain[0]).isNotNull().hasSize(36); // UUID length
        assertThat(MDC.get(RequestIdFilter.MDC_KEY))
                .as("MDC must be cleared after the chain returns")
                .isNull();
        assertThat(res.getHeader("X-Request-Id")).isEqualTo(insideChain[0]);
    }
}
