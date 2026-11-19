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

    @Test
    void validatesRetainedPhaseFiveBRetailerExamplesAndStableIdentities() {
        assertProduct("https://www.meesho.com/bhumi-warm-white-12-meter-decorative-42-warm-white-led-string-light-plug-for-indoor-outdoor-decorationsstring-lights-for-diy-party-home-decor-christmas-diwali-1/p/79g96n", Retailer.MEESHO, "79g96n");
        assertThatThrownBy(() -> validator.validate("https://www.tataneu.com/commerce/product-details?skuId=199318"))
            .isInstanceOf(RuntimeException.class);
        var ajio = validator.validate("https://www.ajio.com/u-s-polo-assn-men-tailored-fit-shirt-with-logo-embroidery/p/442466815_blue");
        assertThat(ajio.productId()).isEqualTo("442466815");
        assertThat(ajio.variantId()).isEqualTo("BLUE");
        assertProduct("https://www.nykaafashion.com/nike-downshifter-14-men-s-road-running-shoe/p/26355138", Retailer.NYKAA_FASHION, "26355138");
        assertProduct("https://www.nykaa.com/wishcare-ceramide-lip-balm-with-spf-50-pa-watermelon/p/13784199?productId=13784199&pps=1", Retailer.NYKAA, "13784199");
        assertProduct("https://www.croma.com/haier-m80f-190-cm-75-inch-3-star-4k-ultra-hd-mini-led-smart-google-tv-with-google-assistant-supported-2025-model-/p/319323", Retailer.CROMA, "319323");
    }

    @Test
    void rejectsRetiredTataNeuAndDistinguishesOtherRetailerHosts() {
        assertThatThrownBy(() -> validator.validate("https://www.tataneu.com/commerce/product-details?skuId=199318"))
            .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> validator.validate("https://www.tataneu.com/commerce/search?skuId=199318"))
            .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> validator.validate("https://nykaa.com.attacker.test/product/p/13784199")).isInstanceOf(RuntimeException.class);
        assertThat(validator.validate("https://www.nykaafashion.com/item/p/26355138").retailer()).isEqualTo(Retailer.NYKAA_FASHION);
        assertThat(validator.validate("https://www.nykaa.com/item/p/13784199").retailer()).isEqualTo(Retailer.NYKAA);
    }

    private void assertProduct(String url, Retailer retailer, String productId) {
        var product = validator.validate(url);
        assertThat(product.retailer()).isEqualTo(retailer);
        assertThat(product.productId()).isEqualTo(productId);
        assertThat(product.canonicalUrl()).doesNotContain("utm_");
    }
}
