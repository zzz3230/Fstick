package ru.fstick.registry_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeBodyLimitFilterTest {

    private static final String CODE_URI = "/api/v1/plugins/p/branches/b/code";

    private final CodeBodyLimitFilter filter = new CodeBodyLimitFilter();

    private MockHttpServletRequest put(String uri, int length) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", uri);
        request.setContent(new byte[length]);
        return request;
    }

    @Test
    void oversizedBody_rejectedWith413() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(put(CODE_URI, 2_200_001), response, chain);

        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("body_too_large"));
        assertNull(chain.getRequest());
    }

    @Test
    void bodyAtLimit_passes() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(put(CODE_URI, 2_200_000), response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void otherPaths_areNotChecked() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(put("/api/v1/plugins/p", 3_000_000), response, chain);

        assertNotNull(chain.getRequest());
    }
}
