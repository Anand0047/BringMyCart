package com.bringmycart.bmc.savedproducts.validation;

import com.bringmycart.bmc.identity.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmazonIndiaProductUrlValidatorTest {
    private final AmazonIndiaProductUrlValidator validator = new AmazonIndiaProductUrlValidator();

    @Test
    void extractsAndNormalizesExactVariantAsinFromSupportedPaths() {
        ValidatedAmazonProductUrl dp = validator.validate("https://www.amazon.in/dp/b0d2mw6l54?th=1&psc=1");
        ValidatedAmazonProductUrl gpProduct = validator.validate("https://amazon.in/gp/product/B0D7SJXCPP/");

        assertThat(dp.asin()).isEqualTo("B0D2MW6L54");
        assertThat(dp.canonicalUrl()).isEqualTo("https://www.amazon.in/dp/B0D2MW6L54");
        assertThat(dp.originalUrl()).isEqualTo("https://www.amazon.in/dp/b0d2mw6l54?th=1&psc=1");
        assertThat(gpProduct.asin()).isEqualTo("B0D7SJXCPP");
        assertThat(gpProduct.canonicalUrl()).isEqualTo("https://www.amazon.in/dp/B0D7SJXCPP");
    }

    @Test
    void rejectsMalformedDeceptiveAndUnsupportedUrls() {
        assertInvalid("http://www.amazon.in/dp/B0D2MW6L54");
        assertInvalid("https://amazon.in.attacker.example/dp/B0D2MW6L54");
        assertInvalid("https://www.amazon.in/product-name/dp/B0D2MW6L54");
        assertInvalid("https://www.amazon.in/dp/TOO-SHORT");
        assertInvalid("https://www.amazon.in/dp/B0D2MW6L54/extra");
        assertInvalid("https://www.amazon.in:8443/dp/B0D2MW6L54");
        assertInvalid("https://user@www.amazon.in/dp/B0D2MW6L54");
        assertInvalid("https://www.amazon.in/dp/B0D2MW6L54#reviews");
    }

    private void assertInvalid(String url) {
        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(ApiException.class)
            .satisfies(exception -> assertThat(((ApiException) exception).getCode()).isEqualTo("invalid_product_url"));
    }
}
