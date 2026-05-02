package org.ngelmakproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling // Spring to process @Scheduled annotations.
@SpringBootApplication
public class NgelmakProjectApplication {
	public static void main(String[] args) {
		SpringApplication.run(NgelmakProjectApplication.class, args);
	}
}
