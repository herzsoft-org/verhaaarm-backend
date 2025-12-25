package moe.herz.verhaarmbackend.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import moe.herz.verhaarmbackend.auth.JwtService;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Collectors;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final UserRepository userRepository;

	public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getServletPath();
		if (path == null) return false;

		// allow unauthenticated auth endpoints
		if (path.equals("/auth/login")
				|| path.equals("/auth/refresh")
				|| path.equals("/auth/logout")) return true;

		// /error should be reachable without auth
		if (path.equals("/error")) return true;

		return false;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain
	) throws ServletException, IOException {

		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = header.substring("Bearer ".length()).trim();

		Claims claims;
		try {
			claims = jwtService.parse(token);
		} catch (Exception e) {
			// invalid / expired token -> continue unauthenticated
			filterChain.doFilter(request, response);
			return;
		}

		String username = claims.getSubject();
		if (username == null || username.isBlank()) {
			filterChain.doFilter(request, response);
			return;
		}

		if (SecurityContextHolder.getContext().getAuthentication() != null) {
			filterChain.doFilter(request, response);
			return;
		}

		UserEntity user = userRepository.findByUsernameWithRoles(username).orElse(null);
		if (user == null || user.isDisabled()) {
			filterChain.doFilter(request, response);
			return;
		}

		var authorities = user.getRoles().stream()
				.map(r -> new SimpleGrantedAuthority("ROLE_" + r.getRole().name()))
				.collect(Collectors.toSet());

		var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
		auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
		SecurityContextHolder.getContext().setAuthentication(auth);

		filterChain.doFilter(request, response);
	}
}
