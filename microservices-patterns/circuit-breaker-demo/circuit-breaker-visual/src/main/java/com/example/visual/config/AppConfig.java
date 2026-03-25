package com.example.visual.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /**
     * Long-timeout RestTemplate for proxying /store/products requests.
     * store-no-cb has readTimeout=60s and supplier SLOW mode delays 30s,
     * so requests can take up to 60s+ (queued requests wait even longer).
     */
    @Bean
    @Qualifier("longTimeout")
    public RestTemplate longTimeoutRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(120_000);
        return new RestTemplate(factory);
    }

    /**
     * Short-timeout RestTemplate for health/status polling endpoints.
     */
    @Bean
    @Qualifier("shortTimeout")
    public RestTemplate shortTimeoutRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(3_000);
        return new RestTemplate(factory);
    }
}
