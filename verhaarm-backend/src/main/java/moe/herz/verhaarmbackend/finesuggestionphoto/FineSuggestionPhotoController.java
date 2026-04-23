package moe.herz.verhaarmbackend.finesuggestionphoto;

import moe.herz.verhaarmbackend.finesuggestionphoto.dto.FineSuggestionPhotoDto;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fine-suggestions/{suggestionId}/photos")
public class FineSuggestionPhotoController {

	private final FineSuggestionPhotoService service;
	private final UserRepository userRepo;

	public FineSuggestionPhotoController(FineSuggestionPhotoService service, UserRepository userRepo) {
		this.service = service;
		this.userRepo = userRepo;
	}

	@GetMapping
	public List<FineSuggestionPhotoDto> list(@PathVariable UUID suggestionId, Authentication auth) {
		return service.list(suggestionId, resolveActor(auth));
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public FineSuggestionPhotoDto upload(
			@PathVariable UUID suggestionId,
			@RequestPart("file") MultipartFile file,
			Authentication auth
	) {
		return service.upload(suggestionId, file, resolveActor(auth));
	}

	@GetMapping("/{photoId}/download")
	public ResponseEntity<Resource> download(
			@PathVariable UUID suggestionId,
			@PathVariable UUID photoId,
			Authentication auth
	) {
		var dl = service.download(suggestionId, photoId, resolveActor(auth));

		MediaType ct = MediaType.APPLICATION_OCTET_STREAM;
		if (dl.contentType() != null && !dl.contentType().isBlank()) {
			try {
				ct = MediaType.parseMediaType(dl.contentType());
			} catch (Exception ignored) {
			}
		}

		return ResponseEntity.ok()
				.contentType(ct)
				.header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionAttachment(dl.filename()))
				.body(dl.resource());
	}

	@DeleteMapping("/{photoId}")
	public ResponseEntity<Void> delete(
			@PathVariable UUID suggestionId,
			@PathVariable UUID photoId,
			Authentication auth
	) {
		service.delete(suggestionId, photoId, resolveActor(auth));
		return ResponseEntity.noContent().build();
	}

	private UserEntity resolveActor(Authentication auth) {
		if (auth == null || auth.getName() == null || auth.getName().isBlank()) return null;
		return userRepo.findByUsernameWithRoles(auth.getName()).orElse(null);
	}

	private static String contentDispositionAttachment(String filename) {
		return "attachment; filename=\"" + filename.replace("\"", "") + "\"";
	}
}