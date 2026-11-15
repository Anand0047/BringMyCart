package com.bringmycart.bmc.savedproducts.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bmc.product-enrichment")
public class ProductEnrichmentProperties {
    private int corePoolSize = 1;
    private int maxPoolSize = 2;
    private int queueCapacity = 10;
    private int maxAttempts = 2;
    private Duration retryDelay = Duration.ofMinutes(15);
    private Duration manualRefreshCooldown = Duration.ofMinutes(5);
    private boolean recoveryEnabled;
    private Duration recoveryInterval = Duration.ofMinutes(15);
    private Duration staleAfter = Duration.ofMinutes(30);
    private int recoveryBatchSize = 10;

    public int getCorePoolSize() { return corePoolSize; }
    public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getRetryDelay() { return retryDelay; }
    public void setRetryDelay(Duration retryDelay) { this.retryDelay = retryDelay; }
    public Duration getManualRefreshCooldown() { return manualRefreshCooldown; }
    public void setManualRefreshCooldown(Duration manualRefreshCooldown) { this.manualRefreshCooldown = manualRefreshCooldown; }
    public boolean isRecoveryEnabled() { return recoveryEnabled; }
    public void setRecoveryEnabled(boolean recoveryEnabled) { this.recoveryEnabled = recoveryEnabled; }
    public Duration getRecoveryInterval() { return recoveryInterval; }
    public void setRecoveryInterval(Duration recoveryInterval) { this.recoveryInterval = recoveryInterval; }
    public Duration getStaleAfter() { return staleAfter; }
    public void setStaleAfter(Duration staleAfter) { this.staleAfter = staleAfter; }
    public int getRecoveryBatchSize() { return recoveryBatchSize; }
    public void setRecoveryBatchSize(int recoveryBatchSize) { this.recoveryBatchSize = recoveryBatchSize; }
}
