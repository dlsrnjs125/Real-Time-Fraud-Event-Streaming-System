package com.example.fraud.api.security;

import com.example.fraud.api.support.exception.ApiErrorCode;
import com.example.fraud.api.support.exception.ErrorResponse;
import com.example.fraud.api.support.logging.TraceIdResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private static final Logger log = LoggerFactory.getLogger(AdminTokenFilter.class);

    private final AdminApiSecurityProperties properties;
    private final ObjectMapper objectMapper;

    public AdminTokenFilter(AdminApiSecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/v1/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String expectedToken = properties.getToken();
        String providedToken = request.getHeader(ADMIN_TOKEN_HEADER);
        if (expectedToken == null || expectedToken.isBlank() || !expectedToken.equals(providedToken)) {
            log.warn("Unauthorized admin API request path={} traceId={}",
                    request.getRequestURI(),
                    TraceIdResolver.resolve(request));
            writeUnauthorized(response, request);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, HttpServletRequest request) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                ApiErrorCode.UNAUTHORIZED_ADMIN_API.name(),
                "Admin token is required",
                TraceIdResolver.resolve(request)
        ));
    }
}
