package com.alisonsfa.SaltoUrl;

import org.springframework.boot.SpringApplication;

public class TestSaltoUrlApplication {

	public static void main(String[] args) {
		SpringApplication.from(SaltoUrlApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
