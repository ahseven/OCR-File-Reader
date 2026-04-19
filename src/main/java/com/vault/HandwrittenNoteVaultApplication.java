package com.vault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HandwrittenNoteVaultApplication {
	public static void main(String[] args) {
		SpringApplication.run(HandwrittenNoteVaultApplication.class, args);
	}
}
