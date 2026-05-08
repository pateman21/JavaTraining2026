package com.example.banking.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(PaymentProcessorProperties.class)
public class RestClientConfig {

    @Bean
    public RestTemplate paymentProcessorRestTemplate(RestTemplateBuilder builder,
                                                     PaymentProcessorProperties props) {
        return builder
                .connectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
                .readTimeout(Duration.ofMillis(props.readTimeoutMs()))
                .rootUri(props.baseUrl())
                .build();
    }
}
