package com.bringmycart.bmc.retailer;

import com.bringmycart.bmc.savedproducts.validation.AmazonIndiaProductUrlValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetailerProductUrlValidatorTest {
    private final RetailerProductUrlValidator validator = new RetailerProductUrlValidator(new AmazonIndiaProductUrlValidator());

    @Test
    void identifiesFlipkartVariantPidAndRemovesTrackingQuery() {
        var product = validator.validate("https://www.flipkart.com/name/p/itm1234567890?pid=MOBABCDE1234&lid=tracking");
        assertThat(product.retailer()).isEqualTo(Retailer.FLIPKART);
        assertThat(product.productId()).isEqualTo("MOBABCDE1234");
        assertThat(product.canonicalUrl()).isEqualTo("https://www.flipkart.com/name/p/itm1234567890?pid=MOBABCDE1234");
    }

    @Test
    void identifiesMyntraNumericProductIdAndRemovesTrackingQuery() {
        var product = validator.validate("https://www.myntra.com/tshirts/roadster/12345678/buy?utm_source=email");
        assertThat(product.retailer()).isEqualTo(Retailer.MYNTRA);
        assertThat(product.productId()).isEqualTo("12345678");
        assertThat(product.canonicalUrl()).isEqualTo("https://www.myntra.com/tshirts/roadster/12345678/buy");
    }

    @Test
    void preservesExistingAmazonCanonicalAsinBehavior() {
        var product = validator.validate("https://www.amazon.in/Phone/dp/b0d2mw6l54/ref=tracking?tag=x");
        assertThat(product.retailer()).isEqualTo(Retailer.AMAZON);
        assertThat(product.productId()).isEqualTo("B0D2MW6L54");
        assertThat(product.canonicalUrl()).isEqualTo("https://www.amazon.in/dp/B0D2MW6L54");
    }

    @Test
    void rejectsSpoofedHostsUnsupportedPagesAndInvalidProductIds() {
        for (String url : new String[] {
            "https://flipkart.com.attacker.test/product/p/item123456",
            "https://www.myntra.com/search?q=shirt",
            "http://www.flipkart.com/name/p/itm1234567890?pid=MOBABCDE1234",
            "https://www.flipkart.com/name/p/itm1234567890?pid=bad"
        }) {
            assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(RuntimeException.class);
        }
    }
}
