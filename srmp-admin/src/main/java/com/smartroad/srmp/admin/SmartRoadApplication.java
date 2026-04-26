package com.smartroad.srmp.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.smartroad.srmp")
public class SmartRoadApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartRoadApplication.class, args);
    }
}
