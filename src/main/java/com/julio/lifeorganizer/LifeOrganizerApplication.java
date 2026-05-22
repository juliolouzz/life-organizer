package com.julio.lifeorganizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LifeOrganizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifeOrganizerApplication.class, args);
    }
}
