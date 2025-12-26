package moe.herz.verhaarmbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration cfg = new CorsConfiguration();

		cfg.setAllowedOriginPatterns(List.of(
				"https://verhaarm.herz.moe",
				"http://localhost:*",
				"http://127.0.0.1:*"
		));

		cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));

		cfg.setAllowedHeaders(List.of(
				"Authorization",
				"Content-Type",
				"Accept",
				"Origin"
		));

		cfg.setExposedHeaders(List.of(
				"Authorization",
				"Content-Disposition"
		));

		// IMPORTANT: only true if you actually use cookies (session/refresh cookie)
		cfg.setAllowCredentials(false);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cfg);
		return source;
	}
}
