package moe.herz.verhaarmbackend.finesuggestionphoto;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.fine.FineEntity;
import moe.herz.verhaarmbackend.fine.FineRepository;
import moe.herz.verhaarmbackend.finephoto.FinePhotoEntity;
import moe.herz.verhaarmbackend.finephoto.FinePhotoRepository;
import moe.herz.verhaarmbackend.finesuggestion.FineSuggestionEntity;
import moe.herz.verhaarmbackend.finesuggestion.FineSuggestionRepository;
import moe.herz.verhaarmbackend.finesuggestion.FineSuggestionStatus;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Service
public class FineSuggestionPhotoService {

	public record Download(Resource resource, String filename, String contentType) {}

	private final FineSuggestionRepository suggestions;
	private final FineSuggestionPhotoRepository suggestionPhotos;
	private final FineRepository fines;
	private final FinePhotoRepository finePhotos;

	private final Path suggestionBaseDir;
	private final Path fineBaseDir;

	@PersistenceContext
	private EntityManager em;

	public FineSuggestionPhotoService(
			FineSuggestionRepository suggestions,
			FineSuggestionPhotoRepository suggestionPhotos,
			FineRepository fines,
			FinePhotoRepository finePhotos,
			@Value("${verhaarm.uploads.fine-suggestions.dir:/var/lib/verhaarm/uploads/fine-suggestions}") String suggestionBaseDir,
			@Value("${verhaarm.uploads.fines.dir:/var/lib/verhaarm/uploads/fines}") String fineBaseDir
	) {
		this.suggestions = suggestions;
		this.suggestionPhotos = suggestionPhotos;
		this.fines = fines;
		this.finePhotos = finePhotos;
		this.suggestionBaseDir = Paths.get(suggestionBaseDir).toAbsolutePath().normalize();
		this.fineBaseDir = Paths.get(fineBaseDir).toAbsolutePath().normalize();
	}

	@Transactional(readOnly = true)
	public List<moe.herz.verhaarmbackend.finesuggestionphoto.dto.FineSuggestionPhotoDto> list(UUID suggestionId, UserEntity actor) {
		FineSuggestionEntity suggestion = suggestions.findVisibleById(suggestionId)
				.orElseThrow(() -> ApiErrors.notFound("Fine suggestion not found"));

		requireCanViewSuggestion(actor, suggestion);

		return suggestionPhotos.findVisibleBySuggestionId(suggestionId).stream()
				.map(this::toDto)
				.toList();
	}

	@Transactional
	public moe.herz.verhaarmbackend.finesuggestionphoto.dto.FineSuggestionPhotoDto upload(UUID suggestionId, MultipartFile file, UserEntity actor) {
		FineSuggestionEntity suggestion = suggestions.findVisibleById(suggestionId)
				.orElseThrow(() -> ApiErrors.notFound("Fine suggestion not found"));

		requireCanUploadPhoto(actor, suggestion);

		if (file == null || file.isEmpty()) throw ApiErrors.badRequest("File required");
		if (file.getSize() <= 0) throw ApiErrors.badRequest("File empty");
		if (file.getSize() > 10L * 1024 * 1024) throw ApiErrors.badRequest("File too large (max 10MB)");

		String contentType = file.getContentType();
		if (contentType == null) contentType = "application/octet-stream";
		if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
			throw ApiErrors.badRequest("Only image uploads are allowed");
		}

		String original = safeOriginalFilename(file.getOriginalFilename());
		String ext = guessExtension(original, contentType);

		String stored = UUID.randomUUID() + ext;
		stored = safeStoredFilename(stored);

		Path suggestionDir = resolveSuggestionDir(suggestionId);
		Path dest = resolveInDir(suggestionDir, stored);

		try {
			Files.createDirectories(suggestionDir);

			String tmpName = safeStoredFilename(stored + ".tmp");
			Path tmp = resolveInDir(suggestionDir, tmpName);

			try (InputStream in = file.getInputStream()) {
				Files.copy(in, tmp, REPLACE_EXISTING);
			}

			try {
				Files.move(tmp, dest, REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(tmp, dest, REPLACE_EXISTING);
			}
		} catch (Exception e) {
			throw ApiErrors.badRequest("Upload failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}

		FineSuggestionPhotoEntity p = new FineSuggestionPhotoEntity(
				UUID.randomUUID(),
				suggestionId,
				actor.getId(),
				original,
				stored,
				contentType,
				file.getSize()
		);

		suggestionPhotos.save(p);

		em.flush();
		em.clear();

		FineSuggestionPhotoEntity reloaded = suggestionPhotos.findVisibleByIdAndSuggestionId(p.getId(), suggestionId)
				.orElseThrow(() -> ApiErrors.notFound("Photo not found"));

		return toDto(reloaded);
	}

	@Transactional(readOnly = true)
	public Download download(UUID suggestionId, UUID photoId, UserEntity actor) {
		FineSuggestionEntity suggestion = suggestions.findVisibleById(suggestionId)
				.orElseThrow(() -> ApiErrors.notFound("Fine suggestion not found"));

		requireCanViewSuggestion(actor, suggestion);

		FineSuggestionPhotoEntity p = suggestionPhotos.findVisibleByIdAndSuggestionId(photoId, suggestionId)
				.orElseThrow(() -> ApiErrors.notFound("Photo not found"));

		Path suggestionDir = resolveSuggestionDir(suggestionId);
		String stored = safeStoredFilename(p.getStoredFilename());
		Path file = resolveInDir(suggestionDir, stored);

		if (!Files.exists(file)) throw ApiErrors.notFound("File missing on disk");

		return new Download(new FileSystemResource(file), p.getOriginalFilename(), p.getContentType());
	}

	@Transactional
	public void delete(UUID suggestionId, UUID photoId, UserEntity actor) {
		FineSuggestionEntity suggestion = suggestions.findVisibleById(suggestionId)
				.orElseThrow(() -> ApiErrors.notFound("Fine suggestion not found"));

		FineSuggestionPhotoEntity p = suggestionPhotos.findVisibleByIdAndSuggestionId(photoId, suggestionId)
				.orElseThrow(() -> ApiErrors.notFound("Photo not found"));

		requireCanDeletePhoto(actor, suggestion, p);

		try {
			Path suggestionDir = resolveSuggestionDir(suggestionId);
			String stored = safeStoredFilename(p.getStoredFilename());
			Path file = resolveInDir(suggestionDir, stored);
			Files.deleteIfExists(file);
		} catch (Exception ignored) {
		}

		suggestionPhotos.delete(p);
	}

	@Transactional
	public void transferSuggestionPhotosToFine(UUID suggestionId, UUID fineId) {
		FineSuggestionEntity suggestion = suggestions.findVisibleById(suggestionId)
				.orElseThrow(() -> ApiErrors.notFound("Fine suggestion not found"));
		FineEntity fine = fines.findVisibleById(fineId)
				.orElseThrow(() -> ApiErrors.notFound("Fine not found"));

		List<FineSuggestionPhotoEntity> sourcePhotos = suggestionPhotos.findVisibleBySuggestionId(suggestionId);
		if (sourcePhotos.isEmpty()) return;

		Path suggestionDir = resolveSuggestionDir(suggestionId);
		Path fineDir = resolveFineDir(fineId);

		try {
			Files.createDirectories(fineDir);
		} catch (Exception e) {
			throw ApiErrors.badRequest("Could not create fine photo directory: " + e.getMessage());
		}

		for (FineSuggestionPhotoEntity src : sourcePhotos) {
			String stored = safeStoredFilename(src.getStoredFilename());
			Path from = resolveInDir(suggestionDir, stored);
			Path to = resolveInDir(fineDir, stored);

			if (!Files.exists(from)) {
				throw ApiErrors.notFound("Suggestion photo file missing on disk");
			}

			try {
				try {
					Files.move(from, to, REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (AtomicMoveNotSupportedException ex) {
					Files.move(from, to, REPLACE_EXISTING);
				}
			} catch (Exception e) {
				throw ApiErrors.badRequest("Could not move suggestion photo to fine: " + e.getMessage());
			}

			FinePhotoEntity dst = new FinePhotoEntity(
					UUID.randomUUID(),
					fine.getId(),
					src.getUploaderUserId(),
					src.getOriginalFilename(),
					src.getStoredFilename(),
					src.getContentType(),
					src.getSizeBytes()
			);

			finePhotos.save(dst);
		}
	}

	@Transactional(readOnly = true)
	public void deleteSuggestionDirectoryBestEffort(UUID suggestionId) {
		Path suggestionDir = resolveSuggestionDir(suggestionId);
		if (!Files.exists(suggestionDir)) return;

		try {
			Files.walk(suggestionDir)
					.sorted(Comparator.reverseOrder())
					.forEach(p -> {
						try { Files.deleteIfExists(p); } catch (Exception ignored) {}
					});
		} catch (Exception ignored) {
		}
	}

	private moe.herz.verhaarmbackend.finesuggestionphoto.dto.FineSuggestionPhotoDto toDto(FineSuggestionPhotoEntity p) {
		return new moe.herz.verhaarmbackend.finesuggestionphoto.dto.FineSuggestionPhotoDto(
				p.getId(),
				p.getSuggestionId(),
				p.getOriginalFilename(),
				p.getContentType(),
				p.getSizeBytes(),
				p.getCreatedAt()
		);
	}

	private static boolean isStaff(UserEntity actor) {
		return actor != null && (
				actor.hasRole(UserRole.ADMIN)
						|| actor.hasRole(UserRole.SENIOR)
						|| actor.hasRole(UserRole.HOUSEKEEPING)
		);
	}

	private static boolean isCreator(UserEntity actor, FineSuggestionEntity suggestion) {
		return actor != null
				&& actor.getId() != null
				&& suggestion.getCreatorUserId() != null
				&& suggestion.getCreatorUserId().equals(actor.getId());
	}

	private static void requireCanViewSuggestion(UserEntity actor, FineSuggestionEntity suggestion) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		if (isStaff(actor)) return;
		if (isCreator(actor, suggestion)) return;
		throw ApiErrors.forbidden("Forbidden");
	}

	private static void requireCanUploadPhoto(UserEntity actor, FineSuggestionEntity suggestion) {
		requireCanViewSuggestion(actor, suggestion);
		if (suggestion.getStatus() != FineSuggestionStatus.PENDING) {
			throw ApiErrors.badRequest("Photos can only be changed while the suggestion is pending");
		}
	}

	private static void requireCanDeletePhoto(UserEntity actor, FineSuggestionEntity suggestion, FineSuggestionPhotoEntity photo) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		if (suggestion.getStatus() != FineSuggestionStatus.PENDING) {
			throw ApiErrors.badRequest("Photos can only be changed while the suggestion is pending");
		}

		if (isStaff(actor)) return;

		if (isCreator(actor, suggestion)) {
			if (photo.getUploaderUserId() == null || photo.getUploaderUserId().equals(actor.getId())) {
				return;
			}
		}

		throw ApiErrors.forbidden("Forbidden");
	}

	private static String safeOriginalFilename(String name) {
		String n = (name == null || name.isBlank()) ? "upload" : name.trim();

		n = n.replace('\\', '/');
		int slash = n.lastIndexOf('/');
		if (slash >= 0) n = n.substring(slash + 1);

		n = n.replaceAll("[\\x00-\\x1F\\x7F]", "");

		if (n.isBlank()) n = "upload";
		if (n.length() > 200) n = n.substring(n.length() - 200);

		return n;
	}

	private static String guessExtension(String original, String contentType) {
		String lower = original.toLowerCase(Locale.ROOT);
		int dot = lower.lastIndexOf('.');
		if (dot >= 0 && dot < lower.length() - 1) {
			String ext = lower.substring(dot);
			if (ext.length() <= 10 && ext.matches("\\.[a-z0-9]+")) return ext;
		}

		String ct = contentType.toLowerCase(Locale.ROOT);
		if (ct.equals("image/jpeg")) return ".jpg";
		if (ct.equals("image/png")) return ".png";
		if (ct.equals("image/webp")) return ".webp";
		return "";
	}

	private Path resolveSuggestionDir(UUID suggestionId) {
		Path dir = suggestionBaseDir.resolve(suggestionId.toString()).normalize();
		if (!dir.startsWith(suggestionBaseDir)) {
			throw ApiErrors.badRequest("Invalid suggestion id");
		}
		return dir;
	}

	private Path resolveFineDir(UUID fineId) {
		Path dir = fineBaseDir.resolve(fineId.toString()).normalize();
		if (!dir.startsWith(fineBaseDir)) {
			throw ApiErrors.badRequest("Invalid fine id");
		}
		return dir;
	}

	private static String safeStoredFilename(String stored) {
		if (stored == null) throw ApiErrors.badRequest("Invalid filename");

		String s = stored.trim();
		if (s.isEmpty()) throw ApiErrors.badRequest("Invalid filename");

		if (s.contains("/") || s.contains("\\") || s.contains("\0")) {
			throw ApiErrors.badRequest("Invalid filename");
		}

		if (!s.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(\\.[a-z0-9]{1,8})?(\\.tmp)?$")) {
			throw ApiErrors.badRequest("Invalid filename");
		}

		return s;
	}

	private static Path resolveInDir(Path dir, String leafName) {
		Path p = dir.resolve(leafName).normalize();
		if (!p.startsWith(dir)) {
			throw ApiErrors.badRequest("Invalid path");
		}
		return p;
	}
}