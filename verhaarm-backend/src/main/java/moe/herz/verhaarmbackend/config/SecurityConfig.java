package moe.herz.verhaarmbackend.config;

import moe.herz.verhaarmbackend.user.UserRepository;
import moe.herz.verhaarmbackend.user.UserRoleEntity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
		http
				// API uses Bearer tokens -> no CSRF needed (unless you later switch to cookie auth)
				.csrf(csrf -> csrf.disable())

				.cors(Customizer.withDefaults())

				// IMPORTANT: stateless API (prevents accidental sessions)
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/auth/**").permitAll()
						.requestMatchers("/error").permitAll()

						// Swagger UI assets must load without JWT (protected by Nginx basic auth)
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**").permitAll()
						.requestMatchers("/v3/api-docs/**").permitAll()

						// lock down actuator (at least health; better to keep exposure minimal in app config)
						.requestMatchers("/actuator/**").hasRole("ADMIN")

						.anyRequest().authenticated()
				)

				// minimal, consistent JSON errors (no stack traces)
				.exceptionHandling(eh -> eh
						.authenticationEntryPoint((request, response, ex) -> {
							response.setStatus(HttpStatus.UNAUTHORIZED.value());
							response.setContentType(MediaType.APPLICATION_JSON_VALUE);
							response.getWriter().write("{\"error\":\"Unauthorized\"}");
						})
						.accessDeniedHandler((request, response, ex) -> {
							response.setStatus(HttpStatus.FORBIDDEN.value());
							response.setContentType(MediaType.APPLICATION_JSON_VALUE);
							response.getWriter().write("{\"error\":\"Forbidden\"}");
						})
				)

				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public UserDetailsService userDetailsService(UserRepository users) {
		return username -> {
			var u = users.findByUsernameWithRoles(username)
					.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

			if (u.isDisabled()) throw new UsernameNotFoundException("User disabled: " + username);

			var authorities = u.getRoles().stream()
					.map(UserRoleEntity::getRole)
					.map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
					.toList();

			return new User(u.getUsername(), u.getPasswordHash(), authorities);
		};
	}

	@Bean
	public AuthenticationManager authenticationManager(
			UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder
	) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}
}
