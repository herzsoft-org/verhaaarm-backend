package moe.herz.verhaarmbackend.paukstunde;

import moe.herz.verhaarmbackend.common.StructuredApiError;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserMemberStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PaukstundeValidator {
	private static final Set<UserMemberStatus> RESTRICTED_SOLO_STATUSES = Set.of(
			UserMemberStatus.FUX,
			UserMemberStatus.SCHUELERFUX,
			UserMemberStatus.KONKNEIPANT
	);

	private PaukstundeValidator() {}

	public static void validate(LocalDate date, Integer hours, Set<UUID> participantUserIds, List<UserEntity> participants) {
		if (date == null) {
			throw StructuredApiError.badRequest(
					"PAUKSTUNDE_DATE_REQUIRED",
					"date required",
					Map.of()
			);
		}
		if (hours == null || hours < 1) {
			throw StructuredApiError.badRequest(
					"PAUKSTUNDE_HOURS_INVALID",
					"hours must be a positive integer",
					Map.of("hours", hours)
			);
		}
		if (participantUserIds == null || participantUserIds.isEmpty()) {
			throw StructuredApiError.badRequest(
					"PAUKSTUNDE_PARTICIPANTS_REQUIRED",
					"At least one participant is required",
					Map.of()
			);
		}
		if (participants.size() != participantUserIds.size()) {
			throw StructuredApiError.badRequest(
					"PAUKSTUNDE_PARTICIPANTS_REQUIRED",
					"All participants must be enabled users",
					Map.of("participantUserIds", participantUserIds)
			);
		}

		boolean hasRestricted = participants.stream()
				.map(PaukstundeValidator::safeStatus)
				.anyMatch(RESTRICTED_SOLO_STATUSES::contains);
		boolean hasUnrestricted = participants.stream()
				.map(PaukstundeValidator::safeStatus)
				.anyMatch(status -> !RESTRICTED_SOLO_STATUSES.contains(status));

		if (hasRestricted && !hasUnrestricted) {
			throw StructuredApiError.badRequest(
					"INVALID_PAUKSTUNDE_PARTICIPANTS",
					"Füxe, Militärfüxe und Konkneipanten dürfen keine Paukstunde ohne mindestens ein Mitglied mit anderem Status eintragen.",
					Map.of(
							"participantUserIds", participantUserIds,
							"restrictedStatuses", RESTRICTED_SOLO_STATUSES.stream().map(Enum::name).sorted().toList()
					)
			);
		}
	}

	private static UserMemberStatus safeStatus(UserEntity user) {
		return user.getMemberStatus() == null ? UserMemberStatus.BURSCH : user.getMemberStatus();
	}
}
