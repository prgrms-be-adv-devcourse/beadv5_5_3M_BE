package com.example.settlementservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class SettlementServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SettlementServiceApplication.class, args);
	}

}
