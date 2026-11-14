package com.ilich.paymentengine.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilich.paymentengine.dto.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class WebhookSignatureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureFilter.class);
    public static final String SIGNATURE_HEADER = "X-Webhook-Signature";

    private final HmacSignatureValidator signatureValidator;
    private final ObjectMapper objectMapper;

    public WebhookSignatureFilter(HmacSignatureValidator signatureValidator, ObjectMapper objectMapper) {
        this.signatureValidator = signatureValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only enforce HMAC signature verification on webhook endpoints
        return !path.startsWith("/api/v1/webhooks");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String signature = request.getHeader(SIGNATURE_HEADER);

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        byte[] bodyBytes = cachedRequest.getCachedBody();

        if (signature == null || signature.isBlank()) {
            log.warn("Rejected webhook request on {}: Missing '{}' header", path, SIGNATURE_HEADER);
            sendUnauthorizedResponse(response, path, "Missing '" + SIGNATURE_HEADER + "' header");
            return;
        }

        if (!signatureValidator.isValidSignature(bodyBytes, signature)) {
            log.warn("Rejected webhook request on {}: Invalid HMAC-SHA256 signature", path);
            sendUnauthorizedResponse(response, path, "Invalid webhook HMAC signature");
            return;
        }

        log.debug("HMAC signature verified successfully for {}", path);
        filterChain.doFilter(cachedRequest, response);
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String path, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                message,
                path
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
