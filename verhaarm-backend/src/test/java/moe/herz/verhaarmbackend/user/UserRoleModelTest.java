package moe.herz.verhaarmbackend.user;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserRoleModelTest {

	@Test
	void fechtwartRoleExists() {
		assertEquals(UserRole.FECHTWART, UserRole.valueOf("FECHTWART"));
	}

	@Test
	void userCanHaveMultipleRolesAtTheSameTime() {
		UserEntity user = new UserEntity(UUID.randomUUID(), "multi-role", "Multi Role", "hash", false);

		user.addRole(UserRole.SENIOR);
		user.addRole(UserRole.HOUSEKEEPING);
		user.addRole(UserRole.FECHTWART);

		assertEquals(Set.of(UserRole.SENIOR, UserRole.HOUSEKEEPING, UserRole.FECHTWART), user.roleSet());
		assertTrue(user.hasRole(UserRole.SENIOR));
		assertTrue(user.hasRole(UserRole.HOUSEKEEPING));
		assertTrue(user.hasRole(UserRole.FECHTWART));
	}
}
