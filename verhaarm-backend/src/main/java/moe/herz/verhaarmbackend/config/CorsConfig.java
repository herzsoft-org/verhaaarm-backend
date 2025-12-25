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

		// Local dev Flutter web origins
		cfg.setAllowedOriginPatterns(List.of(
				"http://localhost:*",
				"http://127.0.0.1:*"
				// add your real web origin here when you have it, e.g.:
				// "https://verhaarm.herz.moe"
		));

		cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));

		// keep explicit, not "*"
		cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		cfg.setExposedHeaders(List.of("Authorization","Content-Disposition"));

		// If you use cookies on web (HttpOnly refresh cookie), this must be true.
		// If you only use Bearer tokens, set this to false.
		cfg.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cfg);
		return source;
	}
}
