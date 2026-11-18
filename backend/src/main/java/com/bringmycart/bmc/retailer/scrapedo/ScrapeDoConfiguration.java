package com.bringmycart.bmc.retailer.scrapedo;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ScrapeDoProperties.class)
public class ScrapeDoConfiguration {
    @Bean("scrapeDoRestClient")
    RestClient scrapeDoRestClient(ScrapeDoProperties properties) {
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(properties.getTimeout())
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(properties.getTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
