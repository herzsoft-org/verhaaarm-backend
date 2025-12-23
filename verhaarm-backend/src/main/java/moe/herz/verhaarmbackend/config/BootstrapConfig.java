package moe.herz.verhaarmbackend.config;

import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import moe.herz.verhaarmbackend.user.UserRole;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

@Configuration
public class BootstrapConfig {
	private static final Logger log = LoggerFactory.getLogger(BootstrapConfig.class);

	@Bean
	public PasswordEncoder passwordEncoder() {
		// Spring defaults are also fine; this is explicit and stable.
		return new BCryptPasswordEncoder();
	}

	@Bean
	public CommandLineRunner bootstrapAdmin(
			UserRepository users,
			PasswordEncoder encoder,
			@Value("${verhaarm.bootstrap.username:}") String username,
			@Value("${verhaarm.bootstrap.displayName:}") String displayName,
			@Value("${verhaarm.bootstrap.password:}") String password
	) {
		return args -> {
			long count = users.count();
			if (count > 0) {
				log.info("Users already exist ({}), skipping bootstrap admin.", count);
				return;
			}

			if (username == null || username.isBlank()
					|| displayName == null || displayName.isBlank()
					|| password == null || password.isBlank()) {
				log.warn("No users exist, but bootstrap vars are missing. " +
						"Set VERHAARM_BOOTSTRAP_USERNAME / _DISPLAYNAME / _PASSWORD to create the first ADMIN.");
				return;
			}

			log.info("No users exist, creating bootstrap admin username='{}'.", username);

			UserEntity admin = new UserEntity(
					UUID.randomUUID(),
					username,
					displayName,
					encoder.encode(password),
					false
			);
			admin.addRole(UserRole.ADMIN);

			// Optional: you can also add MEMBER by default if you want.
			// admin.addRole(UserRole.MEMBER);

			users.save(admin);

			log.info("Bootstrap admin created. You should change the password via later user management.");
		};
	}
}
