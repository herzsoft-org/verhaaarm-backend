package moe.herz.verhaarmbackend.finephoto;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.fine.FineEntity;
import moe.herz.verhaarmbackend.fine.FineRepository;
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
public class FinePhotoService {

	public record Download(Resource resource, String filename, String contentType) {}

	private final FineRepository fines;
	private final FinePhotoRepository photos;

	/** Absolute + normalized base directory for all fine uploads. */
	private final Path baseDir;

	@PersistenceContext
	private EntityManager em;

	public FinePhotoService(
			FineRepository fines,
			FinePhotoRepository photos,
			@Value("${verhaarm.uploads.fines.dir:/var/lib/verhaarm/uploads/fines}") String baseDir
	) {
		this.fines = fines;
		this.photos = photos;
		this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
	}

	@Transactional(readOnly = true)
	public List<moe.herz.verhaarmbackend.finephoto.dto.FinePhotoDto> list(UUID fineId, UserEntity actor) {
		FineEntity fine = fines.findVisibleById(fineId).orElseThrow(() -> ApiErrors.notFound("Fine not found"));
		requireCanViewFine(actor, fine);

		return photos.findVisibleByFineId(fineId).stream()
				.map(this::toDto)
				.toList();
	}

	/**
	 * Upload: allowed for any authenticated user who can VIEW the fine.
	 * (Member-only users can only view fines that target them.)
	 */
	@Transactional
	public moe.herz.verhaarmbackend.finephoto.dto.FinePhotoDto upload(UUID fineId, MultipartFile file, UserEntity actor) {
		FineEntity fine = fines.findVisibleById(fineId).orElseThrow(() -> ApiErrors.notFound("Fine not found"));
		requireCanUploadPhoto(actor, fine);

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

		// stored filename is server-generated and later validated on read/delete
		String stored = UUID.randomUUID() + ext;
		stored = safeStoredFilename(stored);

		Path fineDir = resolveFineDir(fineId);
		Path dest = resolveInFineDir(fineDir, stored);

		try {
			Files.createDirectories(fineDir);

			String tmpName = safeStoredFilename(stored + ".tmp");
			Path tmp = resolveInFineDir(fineDir, tmpName);

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

		FinePhotoEntity p = new FinePhotoEntity(
				UUID.randomUUID(),
				fineId,
				actor.getId(),
				original,
				stored,
				contentType,
				file.getSize()
		);

		photos.save(p);

		em.flush();
		em.clear();

		FinePhotoEntity reloaded = photos.findVisibleByIdAndFineId(p.getId(), fineId)
				.orElseThrow(() -> ApiErrors.notFound("Photo not found"));

		return toDto(reloaded);
	}

	@Transactional(readOnly = true)
	public Download download(UUID fineId, UUID photoId, UserEntity actor) {
		FineEntity fine = fines.findVisibleById(fineId).orElseThrow(() -> ApiErrors.notFound("Fine not found"));
		requireCanViewFine(actor, fine);

		FinePhotoEntity p = photos.findVisibleByIdAndFineId(photoId, fineId)
				.orElseThrow(() -> ApiErrors.notFound("Photo not found"));

		Path fineDir = resolveFineDir(fineId);
		String stored = safeStoredFilename(p.getStoredFilename());
		Path file = resolveInFineDir(fineDir, stored);

		if (!Files.exists(file)) throw ApiErrors.notFound("File missing on disk");
		return new Download(new FileSystemResource(file), p.getOriginalFilename(), p.getContentType());
	}

	/**
	 * Delete:
	 * - ADMIN/SENIOR: allowed
	 * - HOUSEKEEPING: allowed only for own fines
	 * - Otherwise: uploader can delete their own photo (and must be allowed to view the fine)
	 *
	 * Behavior: delete file best-effort + HARD delete DB row.
	 */
	@Transactional
	public void delete(UUID fineId, UUID photoId, UserEntity actor) {
		FineEntity fine = fines.findVisibleById(fineId).orElseThrow(() -> ApiErrors.notFound("Fine not found"));

		FinePhotoEntity p = photos.findVisibleByIdAndFineId(photoId, fineId)
				.orElseThrow(() -> ApiErrors.notFound("Photo not found"));

		requireCanDeletePhoto(actor, fine, p);

		// disk cleanup best-effort
		try {
			Path fineDir = resolveFineDir(fineId);
			String stored = safeStoredFilename(p.getStoredFilename());
			Path file = resolveInFineDir(fineDir, stored);
			Files.deleteIfExists(file);
		} catch (Exception ignored) {
		}

		// hard delete row
		photos.delete(p);
	}

	/**
	 * Used by FineService hard delete. Removes the entire fine directory (best effort).
	 * This does NOT consult the DB and also clears orphaned dirs.
	 */
	@Transactional(readOnly = true)
	public void deleteFineDirectoryBestEffort(UUID fineId) {
		Path fineDir = resolveFineDir(fineId);
		if (!Files.exists(fineDir)) return;

		try {
			Files.walk(fineDir)
					.sorted(Comparator.reverseOrder())
					.forEach(p -> {
						try { Files.deleteIfExists(p); } catch (Exception ignored) {}
					});
		} catch (Exception ignored) {
		}
	}

	private moe.herz.verhaarmbackend.finephoto.dto.FinePhotoDto toDto(FinePhotoEntity p) {
		return new moe.herz.verhaarmbackend.finephoto.dto.FinePhotoDto(
				p.getId(),
				p.getFineId(),
				p.getOriginalFilename(),
				p.getContentType(),
				p.getSizeBytes(),
				p.getCreatedAt()
		);
	}

	private static void requireCanViewFine(UserEntity actor, FineEntity fine) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		boolean isMemberOnly =
				actor.hasRole(UserRole.MEMBER)
						&& actor.getRoles().stream().allMatch(r -> r.getRole() == UserRole.MEMBER);

		if (isMemberOnly && !fine.getTargetUserIds().contains(actor.getId())) {
			throw ApiErrors.forbidden("Forbidden");
		}
	}

	private static void requireCanUploadPhoto(UserEntity actor, FineEntity fine) {
		requireCanViewFine(actor, fine);
	}

	private static void requireCanDeletePhoto(UserEntity actor, FineEntity fine, FinePhotoEntity photo) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		boolean isAdmin = actor.hasRole(UserRole.ADMIN);
		boolean isSenior = actor.hasRole(UserRole.SENIOR);
		boolean isHousekeeping = actor.hasRole(UserRole.HOUSEKEEPING);

		if (isAdmin || isSenior) return;

		if (isHousekeeping) {
			if (!fine.getCreatorUserId().equals(actor.getId())) {
				throw ApiErrors.forbidden("HOUSEKEEPING can only manage photos for own fines");
			}
			return;
		}

		requireCanViewFine(actor, fine);

		if (!photo.getUploaderUserId().equals(actor.getId())) {
			throw ApiErrors.forbidden("Forbidden");
		}
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

	/**
	 * Ensures the fine directory is inside baseDir after normalization.
	 * This is the core path traversal mitigation for fineId-based resolution.
	 */
	private Path resolveFineDir(UUID fineId) {
		Path dir = baseDir.resolve(fineId.toString()).normalize();
		if (!dir.startsWith(baseDir)) {
			throw ApiErrors.badRequest("Invalid fine id");
		}
		return dir;
	}

	/**
	 * Ensures a filename is a simple leaf name we expect (no separators, no traversal)
	 * and matches our server-generated format.
	 */
	private static String safeStoredFilename(String stored) {
		if (stored == null) throw ApiErrors.badRequest("Invalid filename");

		String s = stored.trim();
		if (s.isEmpty()) throw ApiErrors.badRequest("Invalid filename");

		// Reject any path separators (works for both Linux + Windows)
		if (s.contains("/") || s.contains("\\") || s.contains("\0")) {
			throw ApiErrors.badRequest("Invalid filename");
		}

		// Allow: <uuid>[.<ext>][.tmp] where ext is short and alnum
		// Examples:
		//   550e8400-e29b-41d4-a716-446655440000.jpg
		//   550e8400-e29b-41d4-a716-446655440000.webp.tmp
		if (!s.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(\\.[a-z0-9]{1,8})?(\\.tmp)?$")) {
			throw ApiErrors.badRequest("Invalid filename");
		}

		return s;
	}

	/**
	 * Resolve a leaf filename inside a given fine directory and enforce containment.
	 */
	private static Path resolveInFineDir(Path fineDir, String leafName) {
		Path p = fineDir.resolve(leafName).normalize();
		if (!p.startsWith(fineDir)) {
			throw ApiErrors.badRequest("Invalid path");
		}
		return p;
	}
}
