package com.bringmycart.bmc.savedproducts.infrastructure.brightdata;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(BrightDataProperties.class)
public class BrightDataConfiguration {
    @Bean("brightDataRestClient")
    RestClient brightDataRestClient(RestClient.Builder builder, BrightDataProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return builder.baseUrl(properties.getBaseUrl()).requestFactory(requestFactory).build();
    }
}
