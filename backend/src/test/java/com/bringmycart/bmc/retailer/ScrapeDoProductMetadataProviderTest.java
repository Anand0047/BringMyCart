package com.bringmycart.bmc.retailer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.bringmycart.bmc.retailer.scrapedo.RetailerHtmlMetadataExtractor;
import com.bringmycart.bmc.retailer.scrapedo.ScrapeDoProductMetadataProvider;
import com.bringmycart.bmc.retailer.scrapedo.ScrapeDoProperties;
import com.bringmycart.bmc.savedproducts.application.ProductMetadataProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class ScrapeDoProductMetadataProviderTest {
    private static final String TARGET = "https://www.flipkart.com/phone/p/itm1234567890?pid=MOBABCDE1234";
    private static final String HTML = "<script type='application/ld+json'>{\"@type\":\"Product\",\"name\":\"Phone\","
        + "\"offers\":{\"price\":\"7999\",\"priceCurrency\":\"INR\"}}</script>";

    @Test
    void requestsOnlyFixedScrapeDoHostWithEncodedValidatedRetailerUrlAndParsesHtml() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {
            assertThat(request.getMethod().name()).isEqualTo("GET");
            assertThat(request.getURI().getHost()).isEqualTo("api.scrape.do");
            assertThat(request.getURI().getRawQuery()).contains("token=test-token", "url=https%3A%2F%2Fwww.flipkart.com%2Fphone%2Fp%2Fitm1234567890%3Fpid%3DMOBABCDE1234", "disableRetry=true", "disableRedirection=true");
        }).andRespond(withSuccess(HTML, MediaType.TEXT_HTML));

        var metadata = provider(builder, server).fetch(Retailer.FLIPKART, TARGET);
        assertThat(metadata.title()).isEqualTo("Phone");
        assertThat(metadata.finalPrice()).hasToString("7999");
        assertThat(metadata.currency()).isEqualTo("INR");
        assertThat(metadata.source()).isEqualTo("SCRAPEDO");
        server.verify();
    }

    @Test
    void classifiesRateLimitAndServerFailureWithoutExposingResponseBody() {
        for (var status : new HttpStatus[] {HttpStatus.TOO_MANY_REQUESTS, HttpStatus.BAD_GATEWAY}) {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(request -> assertThat(request.getURI().getHost()).isEqualTo("api.scrape.do"))
                .andRespond(withStatus(status).body("private provider detail"));
            var provider = provider(builder, server);
            assertThatThrownBy(() -> provider.fetch(Retailer.FLIPKART, TARGET))
                .isInstanceOf(ProductMetadataProviderException.class)
                .hasMessageNotContaining("private provider detail");
            server.verify();
        }
    }

    @Test
    void logsSanitizedHttpStatusAndClassifiesFiveHundredAsRemoteFailure(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertThat(request.getURI().getHost()).isEqualTo("api.scrape.do"))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("private provider detail"));

        assertThatThrownBy(() -> provider(builder, server).fetch(Retailer.AJIO,
            "https://www.ajio.com/shirt/p/442466815_blue"))
            .isInstanceOfSatisfying(ProductMetadataProviderException.class, failure ->
                assertThat(failure.getFailureType()).isEqualTo(ProductMetadataProviderException.FailureType.REMOTE_FAILURE));

        assertThat(output.getOut()).contains("retailer=AJIO", "httpStatus=502",
            "failureCategory=REMOTE_FAILURE", "exceptionType=HTTP_RESPONSE")
            .doesNotContain("private provider detail", "test-token", "skuId=199318");
        server.verify();
    }

    @Test
    void mapsTransportTimeoutToSafeProviderFailure(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertThat(request.getURI().getHost()).isEqualTo("api.scrape.do"))
            .andRespond(withException(new IOException("transport timeout")));
        assertThatThrownBy(() -> provider(builder, server).fetch(Retailer.FLIPKART, TARGET))
            .isInstanceOfAny(ProductMetadataProviderException.class, ResourceAccessException.class)
            .hasMessageNotContaining("test-token");
        assertThat(output.getOut()).contains("retailer=FLIPKART", "httpStatus=none",
            "failureCategory=TIMEOUT", "exceptionType=ResourceAccessException");
        server.verify();
    }

    @Test
    void rejectsNonRetailerUrlsBeforeAnyProviderRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = provider(builder, server);
        assertThatThrownBy(() -> provider.fetch(Retailer.FLIPKART, "https://attacker.example/p/itm123456"))
            .isInstanceOf(RuntimeException.class);
        server.verify();
    }

    private ScrapeDoProductMetadataProvider provider(RestClient.Builder builder, MockRestServiceServer server) {
        ScrapeDoProperties properties = new ScrapeDoProperties();
        properties.setApiToken("test-token");
        return new ScrapeDoProductMetadataProvider(builder.build(), properties,
            new RetailerProductUrlValidator(new com.bringmycart.bmc.savedproducts.validation.AmazonIndiaProductUrlValidator()),
            new RetailerHtmlMetadataExtractor(new ObjectMapper()));
    }
}
