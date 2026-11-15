package com.bringmycart.bmc.savedproducts.application;

import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;

public interface ProductMetadataProvider {
    ProductMetadata fetch(String asin);
}
