package moe.herz.verhaarmbackend.paukstunde;

import moe.herz.verhaarmbackend.common.ApiValidationException;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserMemberStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaukstundeValidatorTest {

	@Test
	void acceptsSingleUnrestrictedMember() {
		UserEntity bursch = user(UserMemberStatus.BURSCH);

		assertDoesNotThrow(() -> PaukstundeValidator.validate(
				LocalDate.now(),
				1,
				Set.of(bursch.getId()),
				List.of(bursch)
		));
	}

	@Test
	void acceptsFuxWithUnrestrictedMember() {
		UserEntity fux = user(UserMemberStatus.FUX);
		UserEntity inaktiver = user(UserMemberStatus.INAKTIVER);

		assertDoesNotThrow(() -> PaukstundeValidator.validate(
				LocalDate.now(),
				2,
				Set.of(fux.getId(), inaktiver.getId()),
				List.of(fux, inaktiver)
		));
	}

	@Test
	void rejectsFuxAlone() {
		UserEntity fux = user(UserMemberStatus.FUX);

		ApiValidationException ex = assertThrows(ApiValidationException.class, () -> PaukstundeValidator.validate(
				LocalDate.now(),
				1,
				Set.of(fux.getId()),
				List.of(fux)
		));

		assertEquals("INVALID_PAUKSTUNDE_PARTICIPANTS", ex.getCode());
	}

	@Test
	void rejectsOnlyRestrictedStatuses() {
		UserEntity fux = user(UserMemberStatus.FUX);
		UserEntity schuelerfux = user(UserMemberStatus.SCHUELERFUX);
		UserEntity konkneipant = user(UserMemberStatus.KONKNEIPANT);

		ApiValidationException ex = assertThrows(ApiValidationException.class, () -> PaukstundeValidator.validate(
				LocalDate.now(),
				1,
				Set.of(fux.getId(), schuelerfux.getId(), konkneipant.getId()),
				List.of(fux, schuelerfux, konkneipant)
		));

		assertEquals("INVALID_PAUKSTUNDE_PARTICIPANTS", ex.getCode());
	}

	@Test
	void rejectsInvalidHours() {
		UserEntity bursch = user(UserMemberStatus.BURSCH);

		ApiValidationException ex = assertThrows(ApiValidationException.class, () -> PaukstundeValidator.validate(
				LocalDate.now(),
				0,
				Set.of(bursch.getId()),
				List.of(bursch)
		));

		assertEquals("PAUKSTUNDE_HOURS_INVALID", ex.getCode());
	}

	private static UserEntity user(UserMemberStatus status) {
		UUID id = UUID.randomUUID();
		UserEntity user = new UserEntity(id, "user-" + id, "User " + id, "hash", false);
		user.setMemberStatus(status);
		return user;
	}
}
