package com.ilich.paymentengine.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment & Inventory Processing Engine API")
                        .version("1.0.0")
                        .description("Production-ready Payment and Inventory Processing Engine with Idempotency, " +
                                "Pessimistic Concurrency Control, and HMAC-SHA256 Webhook Security.")
                        .contact(new Contact()
                                .name("Ilich Development")
                                .email("dev@ilich.com"))
                        .license(new License().name("Apache 2.0").url("https://spring.io")))
                .components(new Components()
                        .addSecuritySchemes("WebhookSignature", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Webhook-Signature")
                                .description("HMAC-SHA256 signature calculated over the raw request payload")));
    }
}
