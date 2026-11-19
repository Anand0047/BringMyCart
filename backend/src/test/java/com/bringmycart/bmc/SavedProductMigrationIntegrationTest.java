package com.bringmycart.bmc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@Import(TestJwtConfiguration.class)
class SavedProductMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesSavedProductsSchemaAndConstraints() {
        Integer tableCount = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_name = 'saved_products'", Integer.class);
        Integer uniqueConstraintCount = jdbcTemplate.queryForObject(
            "select count(*) from pg_constraint where conname = 'uk_saved_products_user_asin'", Integer.class);
        Integer foreignKeyCount = jdbcTemplate.queryForObject(
            "select count(*) from pg_constraint where conname = 'fk_saved_products_user'", Integer.class);
        Integer variantUniqueConstraintCount = jdbcTemplate.queryForObject(
            "select count(*) from pg_constraint where conname = 'uk_saved_products_user_retailer_product_variant'", Integer.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(uniqueConstraintCount).isEqualTo(1);
        assertThat(foreignKeyCount).isEqualTo(1);
        assertThat(variantUniqueConstraintCount).isEqualTo(1);
    }
}
