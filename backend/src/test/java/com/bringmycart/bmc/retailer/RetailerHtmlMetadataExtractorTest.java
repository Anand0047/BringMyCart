package com.bringmycart.bmc.retailer;

import com.bringmycart.bmc.retailer.scrapedo.RetailerHtmlMetadataExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetailerHtmlMetadataExtractorTest {
    private final RetailerHtmlMetadataExtractor extractor = new RetailerHtmlMetadataExtractor(new ObjectMapper());

    @Test
    void mapsSchemaProductAndOptionalFields() {
        String html = """
            <script type="application/ld+json">{"@context":"https://schema.org","@type":"Product","name":"Phone","image":["https://cdn.example.test/a.jpg"],"brand":{"@type":"Brand","name":"Example"},"description":"A phone","category":"Phones > Android","offers":{"price":"7999","highPrice":"9999","priceCurrency":"INR","availability":"https://schema.org/InStock"},"aggregateRating":{"ratingValue":"4.4","reviewCount":"125"},"additionalProperty":[{"name":"RAM","value":"8 GB"}]}</script>
            """;
        var metadata = extractor.extract(Retailer.FLIPKART, "MOBABCDE1234", html,
            "https://www.flipkart.com/phone/p/item123456?pid=MOBABCDE1234");
        assertThat(metadata.title()).isEqualTo("Phone");
        assertThat(metadata.imageUrl()).isEqualTo("https://cdn.example.test/a.jpg");
        assertThat(metadata.brand()).isEqualTo("Example");
        assertThat(metadata.finalPrice()).hasToString("7999");
        assertThat(metadata.initialPrice()).hasToString("9999");
        assertThat(metadata.availability()).isEqualTo("IN_STOCK");
        assertThat(metadata.rating()).hasToString("4.4");
        assertThat(metadata.reviewsCount()).isEqualTo(125L);
        assertThat(metadata.categories()).containsExactly("Phones", "Android");
        assertThat(metadata.specifications().get(0).get("name").asText()).isEqualTo("RAM");
    }

    @Test
    void ignoresMalformedJsonLdAndFallsBackToOpenGraphWithoutInventingPrice() {
        String html = """
            <script type="application/ld+json">{"@type":"Product","review": }</script>
            <meta property="og:title" content="Myntra shirt"><meta property="og:image" content="https://cdn.example.test/shirt.jpg">
            """;
        var metadata = extractor.extract(Retailer.MYNTRA, "12345678", html,
            "https://www.myntra.com/shirts/brand/12345678/buy");
        assertThat(metadata.title()).isEqualTo("Myntra shirt");
        assertThat(metadata.imageUrl()).isEqualTo("https://cdn.example.test/shirt.jpg");
        assertThat(metadata.finalPrice()).isNull();
        assertThat(metadata.currency()).isNull();
        assertThat(metadata.availability()).isEqualTo("UNKNOWN");
    }

    @Test
    void extractsMyntraProductFieldsWhenReviewBodyContainsUnescapedNewline() {
        String html = """
            <script type="application/ld+json">
            {
              "@context": "https://schema.org",
              "@type": "Product",
              "productID": "28282978",
              "name": "Nike Initiator Men's Running Shoe",
              "image": "https://assets.myntassets.com/product.jpg",
              "offers": {
                "@type": "Offer",
                "price": "5247",
                "priceCurrency": "INR",
                "availability": "https://schema.org/InStock"
              },
              "review": {
                "@type": "Review",
                "reviewBody": "Comfortable for daily running
            and walking"
              }
            }
            </script>
            """;

        var metadata = extractor.extract(Retailer.MYNTRA, "28282978", html,
            "https://www.myntra.com/sports-shoes/nike/nike-initiator-mens-running-shoe/28282978/buy");

        assertThat(metadata.title()).isEqualTo("Nike Initiator Men's Running Shoe");
        assertThat(metadata.finalPrice()).isEqualByComparingTo("5247");
        assertThat(metadata.currency()).isEqualTo("INR");
        assertThat(metadata.imageUrl()).isEqualTo("https://assets.myntassets.com/product.jpg");
        assertThat(metadata.imageUrls()).containsExactly("https://assets.myntassets.com/product.jpg");
        assertThat(metadata.availability()).isEqualTo("IN_STOCK");
    }

    @Test
    void parsesRetailerPriceAndUsesOnlyHttpsImages() {
        String html = "<meta property='product:price:amount' content='₹ 1,299.00'>"
            + "<meta property='og:image' content='http://cdn.example.test/x.jpg'>"
            + "<meta property='og:title' content='   Product   '>";
        var metadata = extractor.extract(Retailer.FLIPKART, "MOBABCDE1234", html,
            "https://www.flipkart.com/p/item12345?pid=MOBABCDE1234");
        assertThat(metadata.title()).isEqualTo("Product");
        assertThat(metadata.finalPrice()).hasToString("1299.00");
        assertThat(metadata.imageUrl()).isNull();
        assertThat(metadata.currency()).isEqualTo("INR");
    }
}
