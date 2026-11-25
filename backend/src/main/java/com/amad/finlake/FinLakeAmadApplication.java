package com.amad.finlake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FinLakeAmadApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinLakeAmadApplication.class, args);
    }
}
