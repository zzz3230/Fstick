package ru.gatewayservice.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserIdSwapFilterTest {

    private static final String MXID = "@user:homeserver.org";

    @Mock
    private IdentityClient identityClient;

    @Mock
    private FilterChain chain;

    private UserIdSwapFilter filter;

    @BeforeEach
    void setUp() {
        filter = new UserIdSwapFilter(new IdentityCache(identityClient));
    }

    @Test
    void mxidHeader_isReplacedWithUuid() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(identityClient.resolve(MXID)).thenReturn(uuid);

        filter.doFilter(requestWithUser(MXID), new MockHttpServletResponse(), chain);

        ArgumentCaptor<HttpServletRequest> forwarded = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(chain).doFilter(forwarded.capture(), any());
        assertEquals(uuid.toString(), forwarded.getValue().getHeader("X-User-Id"));
        assertEquals(uuid.toString(), forwarded.getValue().getHeaders("x-user-id").nextElement());
    }

    @Test
    void secondRequestOfSameUser_hitsCache() throws Exception {
        when(identityClient.resolve(MXID)).thenReturn(UUID.randomUUID());

        filter.doFilter(requestWithUser(MXID), new MockHttpServletResponse(), chain);
        filter.doFilter(requestWithUser(MXID), new MockHttpServletResponse(), chain);

        verify(identityClient, times(1)).resolve(MXID);
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void noHeader_passesThroughUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/plugins");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(identityClient);
    }

    @Test
    void uuidShapedHeader_isRejectedWith400() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithUser(UUID.randomUUID().toString()), response, chain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("invalid_user_id"));
        verifyNoInteractions(chain, identityClient);
    }

    @Test
    void identityServiceDown_returns503() throws Exception {
        when(identityClient.resolve(anyString()))
                .thenThrow(new IdentityUnavailableException("down", new RuntimeException()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithUser(MXID), response, chain);

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("identity_unavailable"));
        verifyNoInteractions(chain);
    }

    @Test
    void cachedUser_stillServedWhenIdentityServiceDown() throws Exception {
        when(identityClient.resolve(MXID)).thenReturn(UUID.randomUUID());
        filter.doFilter(requestWithUser(MXID), new MockHttpServletResponse(), chain);
        reset(identityClient);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithUser(MXID), response, chain);

        assertEquals(200, response.getStatus());
        verify(chain, times(2)).doFilter(any(), any());
    }

    private MockHttpServletRequest requestWithUser(String userId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/plugins/x/state");
        request.addHeader("X-User-Id", userId);
        return request;
    }
}
