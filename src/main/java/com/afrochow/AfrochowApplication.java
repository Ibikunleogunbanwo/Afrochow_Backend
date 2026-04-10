package com.afrochow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
@OpenAPIDefinition(
		info = @Info(
				title = "Afrochow API",
				version = "1.0",
				description = "Afrochow application APIs"
		)
)
@EnableCaching
@EnableScheduling
@EnableAsync
@SpringBootApplication
public class AfrochowApplication {

	public static void main(String[] args) {
		SpringApplication.run(AfrochowApplication.class, args);
	}
}