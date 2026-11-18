package com.bringmycart.bmc.savedproducts.infrastructure.axesso;

import java.time.Clock;
import java.time.YearMonth;

import com.bringmycart.bmc.savedproducts.application.ProductMetadataProviderException;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** A process-local guard; the upstream RapidAPI dashboard remains authoritative for billing. */
@Component
public class AxessoMonthlyRequestQuota {
    private static final Logger log = LoggerFactory.getLogger(AxessoMonthlyRequestQuota.class);
    private final AxessoProperties properties;
    private final Clock clock;
    private YearMonth month;
    private int reserved;

    @Autowired
    public AxessoMonthlyRequestQuota(AxessoProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AxessoMonthlyRequestQuota(AxessoProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.month = YearMonth.now(clock);
    }

    public synchronized void reserveRequest() {
        YearMonth currentMonth = YearMonth.now(clock);
        if (!currentMonth.equals(month)) {
            month = currentMonth;
            reserved = 0;
        }
        if (properties.getMonthlyRequestLimit() <= 0 || reserved >= properties.getMonthlyRequestLimit()) {
            log.warn("Axesso request blocked reason=monthly_quota_exhausted month={} reserved={} limit={}",
                month, reserved, properties.getMonthlyRequestLimit());
            throw new ProductMetadataProviderException(ProductMetadataProviderException.FailureType.QUOTA_EXHAUSTED,
                "Product metadata provider monthly request allowance reached");
        }
        reserved++;
        log.info("Axesso request quota permitted month={} reserved={} limit={}",
            month, reserved, properties.getMonthlyRequestLimit());
    }
}
