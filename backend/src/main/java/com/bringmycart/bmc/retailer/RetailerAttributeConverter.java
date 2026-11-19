package com.bringmycart.bmc.retailer;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Retains readable access to retired retailer rows without exposing retired values
 * as supported application retailers. The database literal remains for V9 compatibility.
 */
@Converter
public class RetailerAttributeConverter implements AttributeConverter<Retailer, String> {
    private static final String RETIRED_TATA_NEU_VALUE = "TATA_NEU";

    @Override
    public String convertToDatabaseColumn(Retailer retailer) {
        if (retailer == null) return null;
        return retailer == Retailer.UNSUPPORTED ? RETIRED_TATA_NEU_VALUE : retailer.name();
    }

    @Override
    public Retailer convertToEntityAttribute(String value) {
        if (value == null) return null;
        if (RETIRED_TATA_NEU_VALUE.equals(value)) return Retailer.UNSUPPORTED;
        try {
            return Retailer.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return Retailer.UNSUPPORTED;
        }
    }
}
