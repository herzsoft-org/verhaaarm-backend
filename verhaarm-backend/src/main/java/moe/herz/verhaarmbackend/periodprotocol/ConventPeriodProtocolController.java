package moe.herz.verhaarmbackend.periodprotocol;

import moe.herz.verhaarmbackend.periodprotocol.dto.ConventPeriodProtocolDto;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/periods/{periodId}/protocol")
public class ConventPeriodProtocolController {

	private final ConventPeriodProtocolService service;
	private final UserRepository userRepo;

	public ConventPeriodProtocolController(
			ConventPeriodProtocolService service,
			UserRepository userRepo
	) {
		this.service = service;
		this.userRepo = userRepo;
	}

	@GetMapping
	public ConventPeriodProtocolDto get(@PathVariable UUID periodId) {
		return service.get(periodId);
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ConventPeriodProtocolDto uploadOrReplace(
			@PathVariable UUID periodId,
			@RequestPart("file") MultipartFile file,
			Authentication auth
	) {
		return service.uploadOrReplace(periodId, file, resolveActor(auth));
	}

	@GetMapping("/file")
	public ResponseEntity<Resource> inlineFile(@PathVariable UUID periodId) {
		var dl = service.download(periodId);

		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition("inline", dl.filename()))
				.body(dl.resource());
	}

	@GetMapping("/download")
	public ResponseEntity<Resource> download(@PathVariable UUID periodId) {
		var dl = service.download(periodId);

		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition("attachment", dl.filename()))
				.body(dl.resource());
	}

	@DeleteMapping
	public ResponseEntity<Void> delete(@PathVariable UUID periodId) {
		service.delete(periodId);
		return ResponseEntity.noContent().build();
	}

	private UserEntity resolveActor(Authentication auth) {
		if (auth == null || auth.getName() == null || auth.getName().isBlank()) return null;
		return userRepo.findByUsernameWithRoles(auth.getName()).orElse(null);
	}

	private static String contentDisposition(String disposition, String filename) {
		String safe = filename == null || filename.isBlank()
				? "protocol.pdf"
				: filename.replace("\"", "");

		String encoded = java.net.URLEncoder.encode(safe, StandardCharsets.UTF_8)
				.replace("+", "%20");

		return disposition + "; filename=\"" + safe + "\"; filename*=UTF-8''" + encoded;
	}
}