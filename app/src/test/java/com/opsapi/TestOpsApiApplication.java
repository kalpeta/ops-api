package com.opsapi;

import org.springframework.boot.SpringApplication;

public class TestOpsApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(OpsApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
