package com.ilich.paymentengine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilich.paymentengine.security.HmacSignatureValidator;
import com.ilich.paymentengine.security.WebhookSignatureFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<WebhookSignatureFilter> webhookSignatureFilterRegistration(
            HmacSignatureValidator signatureValidator,
            ObjectMapper objectMapper) {
        WebhookSignatureFilter filter = new WebhookSignatureFilter(signatureValidator, objectMapper);
        FilterRegistrationBean<WebhookSignatureFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/v1/webhooks/*");
        registration.setName("webhookSignatureFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
