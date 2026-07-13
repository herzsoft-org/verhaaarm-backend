package moe.herz.verhaarmbackend.amt;

import moe.herz.verhaarmbackend.amt.dto.AemterOverviewDto;
import moe.herz.verhaarmbackend.amt.dto.AmtEntryDto;
import moe.herz.verhaarmbackend.amt.dto.AmtGroupLineDto;
import moe.herz.verhaarmbackend.amt.dto.AmtHolderDto;
import moe.herz.verhaarmbackend.amt.dto.AmtSubLineDto;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import moe.herz.verhaarmbackend.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AmtService {

	private final AmtHolderRepository holders;
	private final UserRepository users;
	private final AuditLogService audit;

	public AmtService(AmtHolderRepository holders, UserRepository users, AuditLogService audit) {
		this.holders = holders;
		this.users = users;
		this.audit = audit;
	}

	/**
	 * One non-Ehrengericht office (manual or role-derived), used internally to compute
	 * combination/merge behavior for the Ehrengericht group.
	 */
	private record OtherOffice(String key, String label, List<UserEntity> holderList, int order, boolean autoFromRole) {}

	@Transactional(readOnly = true)
	public AemterOverviewDto getOverview() {
		Map<AmtType, List<UserEntity>> manualByType = new EnumMap<>(AmtType.class);
		for (AmtHolderEntity h : holders.findAllWithUsers()) {
			manualByType.computeIfAbsent(h.getAmtType(), t -> new ArrayList<>()).add(h.getUser());
		}

		List<OtherOffice> otherOffices = new ArrayList<>();
		for (AmtType t : AmtType.values()) {
			if (t.isEhrengericht()) continue;
			otherOffices.add(new OtherOffice(t.name(), t.label(), manualByType.getOrDefault(t, List.of()), t.order(), false));
		}
		for (AutoAmt a : AutoAmt.values()) {
			otherOffices.add(new OtherOffice(a.name(), a.label(), users.findAllEnabledByRole(a.role()), a.order(), true));
		}
		otherOffices.sort(Comparator.comparingInt(OtherOffice::order));

		// userId -> other offices they hold, in canonical order (used to build "x und Y" combos)
		Map<UUID, List<OtherOffice>> otherOfficesByUser = new java.util.HashMap<>();
		for (OtherOffice o : otherOffices) {
			for (UserEntity u : o.holderList()) {
				otherOfficesByUser.computeIfAbsent(u.getId(), k -> new ArrayList<>()).add(o);
			}
		}

		List<AmtGroupLineDto> ehrengericht = new ArrayList<>();
		Set<UUID> ehrengerichtHolderIds = new HashSet<>();

		for (AmtType t : AmtType.values()) {
			if (!t.isEhrengericht()) continue;

			List<UserEntity> slotHolders = manualByType.getOrDefault(t, List.of());
			for (UserEntity u : slotHolders) ehrengerichtHolderIds.add(u.getId());

			List<AmtSubLineDto> lines = new ArrayList<>();
			if (slotHolders.isEmpty()) {
				lines.add(new AmtSubLineDto(t.label(), List.of()));
			} else {
				LinkedHashMap<List<String>, List<UserEntity>> grouped = new LinkedHashMap<>();
				for (UserEntity u : slotHolders) {
					List<String> extraLabels = otherOfficesByUser.getOrDefault(u.getId(), List.of())
							.stream().map(OtherOffice::label).toList();
					grouped.computeIfAbsent(extraLabels, k -> new ArrayList<>()).add(u);
				}
				for (var entry : grouped.entrySet()) {
					String title = entry.getKey().isEmpty()
							? t.label()
							: t.label() + " und " + String.join(" und ", entry.getKey());
					lines.add(new AmtSubLineDto(title, toHolderDtos(entry.getValue())));
				}
			}

			ehrengericht.add(new AmtGroupLineDto(t.name(), t.label(), lines));
		}

		List<AmtEntryDto> other = new ArrayList<>();
		for (OtherOffice o : otherOffices) {
			boolean fullyMergedIntoEhrengericht = !o.holderList().isEmpty()
					&& o.holderList().stream().allMatch(u -> ehrengerichtHolderIds.contains(u.getId()));
			if (fullyMergedIntoEhrengericht) continue;

			other.add(new AmtEntryDto(o.key(), o.label(), o.autoFromRole(), toHolderDtos(o.holderList())));
		}

		return new AemterOverviewDto(ehrengericht, other);
	}

	@Transactional(readOnly = true)
	public boolean isAmtHolder(UserEntity actor) {
		if (actor == null) return false;

		// Admins can manage anything, regardless of whether they personally hold an Amt.
		if (users.hasRole(actor.getId(), UserRole.ADMIN)) return true;

		for (AutoAmt a : AutoAmt.values()) {
			if (users.hasRole(actor.getId(), a.role())) return true;
		}

		return holders.existsByUser_Id(actor.getId());
	}

	@Transactional
	public AmtEntryDto setHolders(AmtType type, List<UUID> userIds, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		if (!isAmtHolder(actor)) throw ApiErrors.forbidden("Forbidden");

		List<UUID> uniqueIds = userIds == null
				? List.of()
				: userIds.stream().filter(Objects::nonNull).distinct().toList();

		Map<UUID, UserEntity> byId = uniqueIds.isEmpty()
				? Map.of()
				: users.findAllById(uniqueIds).stream().collect(Collectors.toMap(UserEntity::getId, u -> u));

		for (UUID id : uniqueIds) {
			if (!byId.containsKey(id)) throw ApiErrors.badRequest("User not found: " + id);
		}

		List<AmtHolderEntity> before = holders.findByAmtType(type);
		List<String> beforeIds = before.stream().map(h -> h.getUser().getId().toString()).sorted().toList();

		holders.deleteByAmtType(type);
		for (UUID id : uniqueIds) {
			holders.save(new AmtHolderEntity(UUID.randomUUID(), type, byId.get(id)));
		}

		var d = audit.obj();
		audit.put(d, "amtType", type.name());
		audit.putStringArray(d, "beforeUserIds", beforeIds);
		audit.putStringArray(d, "afterUserIds", uniqueIds.stream().map(UUID::toString).sorted().toList());
		audit.log(actor, "amt.setHolders", d);

		List<UserEntity> newHolders = uniqueIds.stream().map(byId::get).toList();
		return new AmtEntryDto(type.name(), type.label(), false, toHolderDtos(newHolders));
	}

	private List<AmtHolderDto> toHolderDtos(List<UserEntity> list) {
		return list.stream()
				.sorted(Comparator.comparing(UserEntity::getUsernameNormalized))
				.map(u -> new AmtHolderDto(u.getId(), u.getDisplayName()))
				.toList();
	}
}
