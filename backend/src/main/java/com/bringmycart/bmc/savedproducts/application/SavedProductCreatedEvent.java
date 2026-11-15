package com.bringmycart.bmc.savedproducts.application;

import java.util.UUID;

/** Published inside the save transaction and consumed only after that transaction commits. */
public record SavedProductCreatedEvent(UUID savedProductId) {
}
