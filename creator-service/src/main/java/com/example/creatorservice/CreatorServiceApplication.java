package com.example.creatorservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CreatorServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CreatorServiceApplication.class, args);
	}

}
