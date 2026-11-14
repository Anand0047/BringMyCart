package com.bringmycart.bmc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BringMyCartApplication {

    public static void main(String[] args) {
        SpringApplication.run(BringMyCartApplication.class, args);
    }
}
