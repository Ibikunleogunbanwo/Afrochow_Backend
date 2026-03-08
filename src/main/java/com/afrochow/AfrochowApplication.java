package com.afrochow;

import com.afrochow.config.DotenvConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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
		// Generate password hash first
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		String hash = encoder.encode("SuperAdmin@123");
		System.out.println("SuperAdmin@123 hash: " + hash);

		// Then start the app
		SpringApplication app = new SpringApplication(AfrochowApplication.class);
		app.addInitializers(new DotenvConfig());
		app.run(args);
	}
}