package moe.herz.verhaarmbackend.attendance;

import jakarta.validation.Valid;
import moe.herz.verhaarmbackend.attendance.dto.*;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class AttendanceController {

	private final AttendanceService attendance;
	private final UserRepository users;

	public AttendanceController(AttendanceService attendance, UserRepository users) {
		this.attendance = attendance;
		this.users = users;
	}

	// ---- Global attendance fine config (no period tie)
	@GetMapping("/attendance-fines")
	public AttendanceFineConfigDto getConfig(Authentication auth) {
		return attendance.getConfig(actor(auth));
	}

	@PutMapping("/attendance-fines")
	public AttendanceFineConfigDto setConfig(@RequestBody SetAttendanceFineConfigRequest req, Authentication auth) {
		return attendance.setConfig(req, actor(auth));
	}

	// ---- Attendance exceptions per event
	@GetMapping("/events/{eventId}/attendance")
	public List<AttendanceDto> list(@PathVariable UUID eventId, Authentication auth) {
		return attendance.listForEvent(eventId, actor(auth));
	}

	@PutMapping("/events/{eventId}/attendance")
	public AttendanceDto upsert(@PathVariable UUID eventId, @RequestBody @Valid UpsertAttendanceRequest req, Authentication auth) {
		return attendance.upsert(eventId, req, actor(auth));
	}

	@DeleteMapping("/events/{eventId}/attendance/{userId}")
	public void delete(@PathVariable UUID eventId, @PathVariable UUID userId, Authentication auth) {
		attendance.deleteException(eventId, userId, actor(auth));
	}

	// ---- Generate fines
	@PostMapping("/events/{eventId}/attendance/generate-fines")
	public GenerateAttendanceFinesResultDto generate(@PathVariable UUID eventId, @RequestBody(required = false) GenerateAttendanceFinesRequest req, Authentication auth) {
		return attendance.generateFines(eventId, req, actor(auth));
	}

	private UserEntity actor(Authentication auth) {
		String username = auth.getName();
		return users.findByUsernameWithRoles(username)
				.orElseThrow(() -> moe.herz.verhaarmbackend.common.ApiErrors.unauthorized("Unauthorized"));
	}
}
