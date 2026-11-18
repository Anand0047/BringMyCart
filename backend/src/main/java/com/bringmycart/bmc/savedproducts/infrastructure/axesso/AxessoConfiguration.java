package com.bringmycart.bmc.savedproducts.infrastructure.axesso;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AxessoProperties.class)
public class AxessoConfiguration {
    @Bean("axessoRestClient")
    RestClient axessoRestClient(RestClient.Builder builder, AxessoProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return builder.baseUrl(properties.getBaseUrl()).requestFactory(requestFactory).build();
    }
}
