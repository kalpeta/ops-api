package com.opsapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class OpsApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(OpsApiApplication.class, args);
	}

}
