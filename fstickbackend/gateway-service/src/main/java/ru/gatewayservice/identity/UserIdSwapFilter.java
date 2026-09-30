package ru.gatewayservice.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class UserIdSwapFilter extends OncePerRequestFilter {

    static final String USER_ID_HEADER = "X-User-Id";

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final IdentityCache identityCache;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId == null) {
            chain.doFilter(request, response);
            return;
        }
        if (UUID_PATTERN.matcher(userId).matches()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_user_id",
                    "X-User-Id must be a Matrix user id");
            return;
        }

        String internalUuid;
        try {
            internalUuid = identityCache.resolve(userId).toString();
        } catch (IllegalArgumentException ex) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_user_id",
                    "X-User-Id must be a Matrix user id");
            return;
        } catch (IdentityUnavailableException ex) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "identity_unavailable",
                    "Identity service is unavailable");
            return;
        }
        chain.doFilter(new UserIdRequest(request, internalUuid), response);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    private static final class UserIdRequest extends HttpServletRequestWrapper {

        private final String userId;

        UserIdRequest(HttpServletRequest request, String userId) {
            super(request);
            this.userId = userId;
        }

        @Override
        public String getHeader(String name) {
            return USER_ID_HEADER.equalsIgnoreCase(name) ? userId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return USER_ID_HEADER.equalsIgnoreCase(name)
                    ? Collections.enumeration(Collections.singletonList(userId))
                    : super.getHeaders(name);
        }
    }
}
