package moe.herz.verhaarmbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VerhaarmBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(VerhaarmBackendApplication.class, args);
	}

}
