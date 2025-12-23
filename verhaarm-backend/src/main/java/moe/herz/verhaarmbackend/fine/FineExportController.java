package moe.herz.verhaarmbackend.fine;

import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/fines")
public class FineExportController {

	private final FineExportService export;
	private final UserRepository users;

	public FineExportController(FineExportService export, UserRepository users) {
		this.export = export;
		this.users = users;
	}

	@GetMapping(value = "/export.csv", produces = "text/csv")
	public ResponseEntity<byte[]> exportCsv(
			@RequestParam(required = false) UUID periodId,
			@RequestParam(defaultValue = "false") boolean includeDeleted,
			Authentication auth
	) {
		UserEntity actor = actor(auth);

		var res = export.exportCsv(periodId, includeDeleted, actor);

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + res.filename() + "\"")
				.contentType(new MediaType("text", "csv"))
				.body(res.bytes());
	}

	private UserEntity actor(Authentication auth) {
		String username = auth.getName();
		return users.findByUsernameWithRoles(username)
				.orElseThrow(() -> moe.herz.verhaarmbackend.common.ApiErrors.unauthorized("Unauthorized"));
	}
}
