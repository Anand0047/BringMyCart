package com.bringmycart.bmc.savedproducts.application;

import java.util.UUID;

/** Published in the manual-refresh transaction and consumed only after commit. */
public record SavedProductRefreshRequestedEvent(UUID savedProductId) {
}
