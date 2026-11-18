package com.bringmycart.bmc.savedproducts.infrastructure.axesso;

import java.net.SocketTimeoutException;
import java.util.stream.Stream;

import com.bringmycart.bmc.savedproducts.application.ProductMetadataProviderException;
import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AxessoProductMetadataProviderTest {
    private static final String PRODUCT_URL = "https://www.amazon.in/dp/B0D2MW6L54";
    private MockRestServiceServer server;
    private AxessoProperties properties;
    private AxessoProductMetadataProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://axesso-axesso-amazon-data-service-v1.p.rapidapi.com");
        server = MockRestServiceServer.bindTo(builder).build();
        properties = new AxessoProperties();
        properties.setApiKey("test-key-placeholder");
        provider = new AxessoProductMetadataProvider(builder.build(), properties,
            new AxessoProductMetadataMapper(), new AxessoMonthlyRequestQuota(properties), new ObjectMapper());
    }

    @Test
    void sendsVerifiedRapidApiRequestAndMapsSupportedAmazonIndiaFields() {
        expectProductRequest().andRespond(withSuccess("""
            {
              "asin":"B0D2MW6L54", "parentAsin":"B0PARENT01",
              "productTitle":"Nothing Ear (a)", "price":"INR 7,999.00", "retailPrice":"INR 9,999.00",
              "mainImage":{"imageUrl":"https://images.example/main.jpg"},
              "imageUrlList":["https://images.example/other.jpg"],
              "productDescription":"Not persisted by the current model",
              "warehouseAvailability":"In Stock", "productRating":"4.2 out of 5 stars",
              "countReview":"52", "features":["feature"], "categories":["Electronics"],
              "productDetails":[{"name":"Model","value":"Example"}],
              "manufacturer":"Nothing", "currency":null, "timestamp":"2026-11-15T10:00:00Z",
              "dealPrice":0, "salePrice":0, "customers_say":"Not persisted"
            }
            """, MediaType.APPLICATION_JSON));

        ProductMetadata metadata = provider.fetch("B0D2MW6L54");

        assertThat(metadata.asin()).isEqualTo("B0D2MW6L54");
        assertThat(metadata.parentAsin()).isEqualTo("B0PARENT01");
        assertThat(metadata.title()).isEqualTo("Nothing Ear (a)");
        assertThat(metadata.brand()).isEqualTo("Nothing");
        assertThat(metadata.imageUrl()).isEqualTo("https://images.example/main.jpg");
        assertThat(metadata.initialPrice()).isEqualByComparingTo("9999");
        assertThat(metadata.finalPrice()).isEqualByComparingTo("7999");
        assertThat(metadata.discountPercentage()).isEqualByComparingTo("20.00");
        assertThat(metadata.currency()).isEqualTo("INR");
        assertThat(metadata.rating()).isEqualByComparingTo("4.2");
        assertThat(metadata.reviewsCount()).isEqualTo(52L);
        assertThat(metadata.availability()).isEqualTo("In Stock");
        assertThat(metadata.providerFetchedAt()).isNotNull();
        server.verify();
    }

    @Test
    void encodesProductUrlAndFallsBackToFirstAvailableImage() {
        expectProductRequest().andRespond(withSuccess("""
            {"asin":"B0D2MW6L54","price":"INR 0","retailPrice":null,"currency":null,
             "mainImage":{"imageUrl":""},"imageUrlList":["https://images.example/first.jpg"]}
            """, MediaType.APPLICATION_JSON));
        ProductMetadata metadata = provider.fetch("b0d2mw6l54");
        assertThat(metadata.imageUrl()).isEqualTo("https://images.example/first.jpg");
        assertThat(metadata.finalPrice()).isEqualByComparingTo("0");
        assertThat(metadata.discountPercentage()).isNull();
        assertThat(metadata.currency()).isEqualTo("INR");
        server.verify();
    }

    @Test
    void acceptsPartialResponseButRejectsWrongAsinAndEmptyResponse() {
        expectProductRequest().andRespond(withSuccess("{\"asin\":\"B0D2MW6L54\"}", MediaType.APPLICATION_JSON));
        assertThat(provider.fetch("B0D2MW6L54").title()).isNull();
        server.verify();

        setUp();
        expectProductRequest().andRespond(withSuccess("{\"asin\":\"B0D7SJXCPP\"}", MediaType.APPLICATION_JSON));
        assertFailure(ProductMetadataProviderException.FailureType.INVALID_RESPONSE,
            () -> provider.fetch("B0D2MW6L54"));

        setUp();
        expectProductRequest().andRespond(withSuccess("{malformed", MediaType.APPLICATION_JSON));
        assertFailure(ProductMetadataProviderException.FailureType.INVALID_RESPONSE,
            () -> provider.fetch("B0D2MW6L54"));

        setUp();
        expectProductRequest().andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertFailure(ProductMetadataProviderException.FailureType.INVALID_RESPONSE,
            () -> provider.fetch("B0D2MW6L54"));
    }

    @ParameterizedTest
    @MethodSource("providerFailures")
    void classifiesHttpFailuresWithoutLeakingProviderResponse(HttpStatus status,
            ProductMetadataProviderException.FailureType expected) {
        expectProductRequest().andRespond(withStatus(status));
        assertFailure(expected, () -> provider.fetch("B0D2MW6L54"));
    }

    @Test
    void classifiesTimeoutAndDoesNotSendWhenUnconfiguredOrQuotaExhausted() {
        expectProductRequest().andRespond(withException(new SocketTimeoutException("transport details")));
        assertFailure(ProductMetadataProviderException.FailureType.TIMEOUT,
            () -> provider.fetch("B0D2MW6L54"));

        setUp();
        properties.setApiKey("");
        assertFailure(ProductMetadataProviderException.FailureType.NOT_CONFIGURED,
            () -> provider.fetch("B0D2MW6L54"));
        server.verify();

        setUp();
        properties.setMonthlyRequestLimit(1);
        expectProductRequest().andRespond(withSuccess("{\"asin\":\"B0D2MW6L54\"}", MediaType.APPLICATION_JSON));
        provider.fetch("B0D2MW6L54");
        assertFailure(ProductMetadataProviderException.FailureType.QUOTA_EXHAUSTED,
            () -> provider.fetch("B0D2MW6L54"));
        server.verify();
    }

    private org.springframework.test.web.client.ResponseActions expectProductRequest() {
        return server.expect(requestTo("https://axesso-axesso-amazon-data-service-v1.p.rapidapi.com/amz/amazon-lookup-product?url="
                + "https%3A%2F%2Fwww.amazon.in%2Fdp%2FB0D2MW6L54"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(request -> {
                String rawQuery = request.getURI().getRawQuery();
                assertThat(rawQuery).isEqualTo("url=https%3A%2F%2Fwww.amazon.in%2Fdp%2FB0D2MW6L54");
                assertThat(rawQuery).doesNotContain("%253A", "%252F");
            })
            .andExpect(header("x-rapidapi-key", "test-key-placeholder"))
            .andExpect(header("x-rapidapi-host", "axesso-axesso-amazon-data-service-v1.p.rapidapi.com"));
    }

    private void assertFailure(ProductMetadataProviderException.FailureType expected,
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(ProductMetadataProviderException.class)
            .satisfies(exception -> assertThat(((ProductMetadataProviderException) exception).getFailureType())
                .isEqualTo(expected));
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
