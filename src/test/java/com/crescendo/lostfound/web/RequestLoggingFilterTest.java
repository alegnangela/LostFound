package com.crescendo.lostfound.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void generatesARequestIdVisibleInTheMdcDuringTheRequestAndEchoedInTheResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> idSeenByHandler = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/lost-items"), response,
                (req, res) -> idSeenByHandler.set(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY)));

        assertThat(idSeenByHandler.get()).isNotBlank();
        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo(idSeenByHandler.get());
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void reusesAWellFormedIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lost-items");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "gateway-42.abc_DEF");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("gateway-42.abc_DEF");
    }

    @Test
    void replacesAMalformedIncomingRequestIdToPreventLogInjection() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lost-items");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "fake\nINFO forged log line");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER))
                .isNotEqualTo("fake\nINFO forged log line")
                .matches("[0-9a-f-]{36}");
    }

    @Test
    void clearsTheMdcEvenWhenTheRequestFails() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/lost-items"), response,
                    (req, res) -> { throw new IllegalStateException("boom"); });
        } catch (Exception expected) {
            // the failure itself is not what this test is about
        }

        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY)).isNull();
    }
}
