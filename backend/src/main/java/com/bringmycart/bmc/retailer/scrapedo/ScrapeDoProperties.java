package com.bringmycart.bmc.retailer.scrapedo;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bmc.scrapedo")
public class ScrapeDoProperties {
    private String apiToken = "";
    private boolean enabled = true;
    private Duration timeout = Duration.ofSeconds(20);
    private int maxResponseBytes = 2_000_000;

    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) {
        this.timeout = timeout == null ? Duration.ofSeconds(20)
            : timeout.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1)
            : timeout.compareTo(Duration.ofSeconds(60)) > 0 ? Duration.ofSeconds(60) : timeout;
    }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int maxResponseBytes) {
        this.maxResponseBytes = Math.max(1024, Math.min(maxResponseBytes, 5_000_000));
    }
}
