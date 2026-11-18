package com.bringmycart.bmc.savedproducts.infrastructure.axesso;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bmc.axesso")
public class AxessoProperties {
    private String baseUrl = "https://axesso-axesso-amazon-data-service-v1.p.rapidapi.com";
    private String host = "axesso-axesso-amazon-data-service-v1.p.rapidapi.com";
    private String apiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(30);
    private int monthlyRequestLimit = 100;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getMonthlyRequestLimit() { return monthlyRequestLimit; }
    public void setMonthlyRequestLimit(int monthlyRequestLimit) { this.monthlyRequestLimit = monthlyRequestLimit; }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && host != null && !host.isBlank();
    }
}
