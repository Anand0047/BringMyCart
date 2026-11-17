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
        ValidatedAmazonProductUrl withFragment = validator.validate("https://www.amazon.in/dp/B0D2MW6L54?th=1#reviews");
        ValidatedAmazonProductUrl gpProduct = validator.validate("https://amazon.in/gp/product/B0D7SJXCPP/");
        ValidatedAmazonProductUrl titledReferral = validator.validate(
            "https://www.amazon.in/WZATCO-Official-Netflix-Google-Projector/dp/B0H42KDFN8/ref=pd_sbs_d_sccl_1_7/258-9384714-2049055");
        ValidatedAmazonProductUrl cartReferral = validator.validate(
            "https://www.amazon.in/gp/product/B0D2MW6L54/ref=ewc_pr_img_1?smid=AUCQGA5A6IVIW&psc=1");
        ValidatedAmazonProductUrl titledDp = validator.validate(
            "https://www.amazon.in/Spigen-Transparent-Protection-Thermoplastic-Polyurethane/dp/B0GKQFH9VG");
        ValidatedAmazonProductUrl caseVariant = validator.validate("https://www.amazon.in/gP/PrOdUcT/b07d4jbcsy/ref=product_page?smid=seller");
        ValidatedAmazonProductUrl mobileVariant = validator.validate("https://www.amazon.in/gp/aw/d/B0FDKZMMYC?psc=1");

        assertThat(dp.asin()).isEqualTo("B0D2MW6L54");
        assertThat(dp.canonicalUrl()).isEqualTo("https://www.amazon.in/dp/B0D2MW6L54");
        assertThat(dp.originalUrl()).isEqualTo("https://www.amazon.in/dp/b0d2mw6l54?th=1&psc=1");
        assertThat(withFragment.originalUrl()).isEqualTo("https://www.amazon.in/dp/B0D2MW6L54?th=1#reviews");
        assertThat(withFragment.canonicalUrl()).isEqualTo("https://www.amazon.in/dp/B0D2MW6L54");
        assertThat(gpProduct.asin()).isEqualTo("B0D7SJXCPP");
        assertThat(gpProduct.canonicalUrl()).isEqualTo("https://www.amazon.in/dp/B0D7SJXCPP");
        assertThat(titledReferral.asin()).isEqualTo("B0H42KDFN8");
        assertThat(titledReferral.canonicalUrl()).isEqualTo("https://www.amazon.in/dp/B0H42KDFN8");
        assertThat(cartReferral.asin()).isEqualTo("B0D2MW6L54");
        assertThat(cartReferral.canonicalUrl()).isEqualTo("https://www.amazon.in/dp/B0D2MW6L54");
        assertThat(titledDp.asin()).isEqualTo("B0GKQFH9VG");
        assertThat(caseVariant.asin()).isEqualTo("B07D4JBCSY");
        assertThat(mobileVariant.asin()).isEqualTo("B0FDKZMMYC");
    }

    @Test
    void rejectsMalformedDeceptiveAndUnsupportedUrls() {
        assertInvalid("http://www.amazon.in/dp/B0D2MW6L54");
        assertInvalid("https://amazon.in.attacker.example/dp/B0D2MW6L54");
        assertInvalid("https://www.amazon.in/dp/TOO-SHORT");
        assertInvalid("https://www.amazon.in/dp/B0D2MW6L54/extra");
        assertInvalid("https://www.amazon.in/product-name/dp/B0D2MW6L54/extra");
        assertInvalid("https://www.amazon.in/gp/product/B0D2MW6L54/extra");
        assertInvalid("https://www.amazon.in:8443/dp/B0D2MW6L54");
        assertInvalid("https://user@www.amazon.in/dp/B0D2MW6L54");
    }

    private void assertInvalid(String url) {
        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOf(ApiException.class)
            .satisfies(exception -> assertThat(((ApiException) exception).getCode()).isEqualTo("invalid_product_url"));
    }
}
