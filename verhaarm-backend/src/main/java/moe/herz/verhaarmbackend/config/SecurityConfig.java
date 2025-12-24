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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
		http
				// IMPORTANT for Flutter Web / browser (CORS preflight)
				.cors(withDefaults())

				.csrf(csrf -> csrf.disable())
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						// Preflight must be allowed
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

						.requestMatchers("/auth/**").permitAll()
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
						.requestMatchers("/actuator/health").permitAll()
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated()
				)
				.exceptionHandling(eh -> eh
						// no/invalid auth => 401
						.authenticationEntryPoint((request, response, ex) -> {
							response.setStatus(HttpStatus.UNAUTHORIZED.value());
							response.setContentType(MediaType.APPLICATION_JSON_VALUE);
							response.getWriter().write("{\"error\":\"Unauthorized\"}");
						})
						// authenticated but not allowed => 403
						.accessDeniedHandler((request, response, ex) -> {
							response.setStatus(HttpStatus.FORBIDDEN.value());
							response.setContentType(MediaType.APPLICATION_JSON_VALUE);
							response.getWriter().write("{\"error\":\"Forbidden\"}");
						})
				)
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	/**
	 * Backing store for username/password auth used by /auth/login.
	 * Loads users from DB and maps roles to authorities.
	 */
	@Bean
	public UserDetailsService userDetailsService(UserRepository users) {
		return username -> {
			var u = users.findByUsernameWithRoles(username)
					.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

			if (u.isDisabled()) {
				// authentication should fail for disabled users
				throw new UsernameNotFoundException("User disabled: " + username);
			}

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

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
