package com.kkodong.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KkodongServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(KkodongServerApplication.class, args);
	}

}
