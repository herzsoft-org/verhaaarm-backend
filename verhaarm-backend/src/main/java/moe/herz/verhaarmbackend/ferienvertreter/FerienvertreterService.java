package moe.herz.verhaarmbackend.ferienvertreter;

import moe.herz.verhaarmbackend.amt.AmtService;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.ferienvertreter.dto.CreateFerienvertreterRequest;
import moe.herz.verhaarmbackend.ferienvertreter.dto.FerienvertreterDto;
import moe.herz.verhaarmbackend.ferienvertreter.dto.UpdateFerienvertreterRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import moe.herz.verhaarmbackend.user.dto.UserPickerDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FerienvertreterService {

	private final FerienvertreterRepository ferienvertreter;
	private final UserRepository users;
	private final AmtService amt;
	private final AuditLogService audit;

	public FerienvertreterService(
			FerienvertreterRepository ferienvertreter,
			UserRepository users,
			AmtService amt,
			AuditLogService audit
	) {
		this.ferienvertreter = ferienvertreter;
		this.users = users;
		this.amt = amt;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<FerienvertreterDto> list() {
		return ferienvertreter.findAllOrderedWithUser().stream().map(this::toDto).toList();
	}

	@Transactional
	public FerienvertreterDto create(CreateFerienvertreterRequest req, UserEntity actor) {
		requireAmtHolder(actor);

		if (req.untilDate().isBefore(req.fromDate())) {
			throw ApiErrors.badRequest("untilDate must not be before fromDate");
		}

		UserEntity person = users.findById(req.userId())
				.orElseThrow(() -> ApiErrors.badRequest("User not found: " + req.userId()));

		FerienvertreterEntity e = new FerienvertreterEntity(UUID.randomUUID(), person, req.fromDate(), req.untilDate());
		ferienvertreter.save(e);

		var d = audit.obj();
		audit.put(d, "ferienvertreterId", e.getId());
		audit.put(d, "userId", person.getId());
		audit.put(d, "fromDate", e.getFromDate().toString());
		audit.put(d, "untilDate", e.getUntilDate().toString());
		audit.log(actor, "ferienvertreter.create", d);

		return toDto(e);
	}

	@Transactional
	public FerienvertreterDto update(UUID id, UpdateFerienvertreterRequest req, UserEntity actor) {
		requireAmtHolder(actor);

		FerienvertreterEntity e = ferienvertreter.findById(id)
				.orElseThrow(() -> ApiErrors.notFound("Ferienvertreter not found"));

		UUID beforeUserId = e.getUser().getId();
		var beforeFrom = e.getFromDate();
		var beforeUntil = e.getUntilDate();

		if (req.userId() != null) {
			UserEntity person = users.findById(req.userId())
					.orElseThrow(() -> ApiErrors.badRequest("User not found: " + req.userId()));
			e.setUser(person);
		}

		var newFrom = req.fromDate() != null ? req.fromDate() : e.getFromDate();
		var newUntil = req.untilDate() != null ? req.untilDate() : e.getUntilDate();
		if (newUntil.isBefore(newFrom)) {
			throw ApiErrors.badRequest("untilDate must not be before fromDate");
		}
		e.setFromDate(newFrom);
		e.setUntilDate(newUntil);

		ferienvertreter.save(e);

		var d = audit.obj();
		audit.put(d, "ferienvertreterId", e.getId());
		audit.put(d, "beforeUserId", beforeUserId);
		audit.put(d, "afterUserId", e.getUser().getId());
		audit.put(d, "beforeFromDate", beforeFrom.toString());
		audit.put(d, "afterFromDate", e.getFromDate().toString());
		audit.put(d, "beforeUntilDate", beforeUntil.toString());
		audit.put(d, "afterUntilDate", e.getUntilDate().toString());
		audit.log(actor, "ferienvertreter.update", d);

		return toDto(e);
	}

	@Transactional
	public void delete(UUID id, UserEntity actor) {
		requireAmtHolder(actor);

		FerienvertreterEntity e = ferienvertreter.findById(id)
				.orElseThrow(() -> ApiErrors.notFound("Ferienvertreter not found"));

		ferienvertreter.delete(e);

		var d = audit.obj();
		audit.put(d, "ferienvertreterId", id);
		audit.log(actor, "ferienvertreter.delete", d);
	}

	private void requireAmtHolder(UserEntity actor) {
		if (actor == null || !amt.isAmtHolder(actor)) throw ApiErrors.forbidden("Forbidden");
	}

	private FerienvertreterDto toDto(FerienvertreterEntity e) {
		UserEntity u = e.getUser();
		UserPickerDto person = new UserPickerDto(
				u.getId(),
				u.getUsername(),
				u.getDisplayName(),
				u.getMemberStatus() == null ? "BURSCH" : u.getMemberStatus().name(),
				u.getMemberStatus() == null || u.getMemberStatus().isAktivitas(),
				u.isDisabled()
		);

		return new FerienvertreterDto(e.getId(), person, e.getFromDate(), e.getUntilDate());
	}
}
