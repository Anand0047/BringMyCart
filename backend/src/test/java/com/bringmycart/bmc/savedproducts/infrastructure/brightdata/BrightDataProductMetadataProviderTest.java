package com.bringmycart.bmc.savedproducts.infrastructure.brightdata;

import java.net.SocketTimeoutException;
import java.util.stream.Stream;

import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BrightDataProductMetadataProviderTest {
    private MockRestServiceServer server;
    private BrightDataProductMetadataProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.brightdata.com");
        server = MockRestServiceServer.bindTo(builder).build();
        BrightDataProperties properties = new BrightDataProperties();
        properties.setApiKey("test-api-key");
        properties.setDatasetId("test-dataset");
        provider = new BrightDataProductMetadataProvider(builder.build(), properties,
            new BrightDataProductMetadataMapper());
    }

    @Test
    void mapsValidResponseAndCalculatesDiscountWithoutPersistingReviewData() {
        expectScrape().andRespond(withSuccess("""
            [{
              "asin":"b0d2mw6l54", "parent_asin":"B0PARENT01", "title":"Example variant",
              "brand":"Example", "image_url":"https://images.example/product.jpg",
              "initial_price":"₹9,999", "final_price":7999, "currency":"inr",
              "rating":"4.2", "reviews_count":"52", "availability":"In stock",
              "timestamp":"2026-11-15T10:00:00Z", "customers_say":"Not mapped"
            }]
            """, MediaType.APPLICATION_JSON));

        ProductMetadata metadata = provider.fetch("B0D2MW6L54");

        assertThat(metadata.asin()).isEqualTo("B0D2MW6L54");
        assertThat(metadata.parentAsin()).isEqualTo("B0PARENT01");
        assertThat(metadata.initialPrice()).isEqualByComparingTo("9999");
        assertThat(metadata.finalPrice()).isEqualByComparingTo("7999");
        assertThat(metadata.discountPercentage()).isEqualByComparingTo("20.00");
        assertThat(metadata.currency()).isEqualTo("INR");
        assertThat(metadata.rating()).isEqualByComparingTo("4.2");
        assertThat(metadata.reviewsCount()).isEqualTo(52L);
        server.verify();
    }

    @Test
    void acceptsPartialMetadataButRejectsAnUnexpectedAsin() {
        expectScrape().andRespond(withSuccess("[{\"asin\":\"B0D2MW6L54\"}]", MediaType.APPLICATION_JSON));
        ProductMetadata partial = provider.fetch("B0D2MW6L54");
        assertThat(partial.title()).isNull();
        assertThat(partial.initialPrice()).isNull();
        assertThat(partial.discountPercentage()).isNull();
        server.verify();

        setUp();
        expectScrape().andRespond(withSuccess("[{\"asin\":\"B0D7SJXCPP\"}]", MediaType.APPLICATION_JSON));
        assertFailure(ProductMetadataProviderException.FailureType.INVALID_RESPONSE,
            () -> provider.fetch("B0D2MW6L54"));
    }

    @Test
    void rejectsEmptyAndMalformedResponses() {
        expectScrape().andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertFailure(ProductMetadataProviderException.FailureType.INVALID_RESPONSE,
            () -> provider.fetch("B0D2MW6L54"));

        setUp();
        expectScrape().andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));
        assertFailure(ProductMetadataProviderException.FailureType.INVALID_RESPONSE,
            () -> provider.fetch("B0D2MW6L54"));
    }

    @ParameterizedTest
    @MethodSource("providerFailures")
    void classifiesProviderHttpFailures(HttpStatus status, ProductMetadataProviderException.FailureType expected) {
        expectScrape().andRespond(withStatus(status));
        assertFailure(expected, () -> provider.fetch("B0D2MW6L54"));
    }

    @Test
    void classifiesTransportTimeoutsAndRefusesMissingConfiguration() {
        expectScrape().andRespond(withException(new SocketTimeoutException("read timed out")));
        assertFailure(ProductMetadataProviderException.FailureType.TIMEOUT,
            () -> provider.fetch("B0D2MW6L54"));

        BrightDataProperties unconfigured = new BrightDataProperties();
        unconfigured.setApiKey("");
        BrightDataProductMetadataProvider unconfiguredProvider = new BrightDataProductMetadataProvider(
            RestClient.create(), unconfigured, new BrightDataProductMetadataMapper());
        assertFailure(ProductMetadataProviderException.FailureType.NOT_CONFIGURED,
            () -> unconfiguredProvider.fetch("B0D2MW6L54"));
    }

    private org.springframework.test.web.client.ResponseActions expectScrape() {
        return server.expect(requestTo("https://api.brightdata.com/datasets/v3/scrape?dataset_id=test-dataset&format=json"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-api-key"))
            .andExpect(content().json("{\"input\":[{\"url\":\"https://www.amazon.in/dp/B0D2MW6L54\"}]}"));
    }

    private void assertFailure(ProductMetadataProviderException.FailureType expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
            .isInstanceOf(ProductMetadataProviderException.class)
            .satisfies(exception -> assertThat(((ProductMetadataProviderException) exception).getFailureType()).isEqualTo(expected));
    }

    private static Stream<Arguments> providerFailures() {
        return Stream.of(
            Arguments.of(HttpStatus.UNAUTHORIZED, ProductMetadataProviderException.FailureType.AUTHENTICATION),
            Arguments.of(HttpStatus.FORBIDDEN, ProductMetadataProviderException.FailureType.AUTHENTICATION),
            Arguments.of(HttpStatus.TOO_MANY_REQUESTS, ProductMetadataProviderException.FailureType.RATE_LIMITED),
            Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, ProductMetadataProviderException.FailureType.REMOTE_FAILURE)
        );
    }
}
