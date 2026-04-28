package moe.herz.verhaarmbackend.periodprotocol;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.period.ConventPeriodRepository;
import moe.herz.verhaarmbackend.periodprotocol.dto.ConventPeriodProtocolDto;
import moe.herz.verhaarmbackend.user.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Service
public class ConventPeriodProtocolService {

	public record Download(Resource resource, String filename, String contentType) {}

	private final ConventPeriodRepository periods;
	private final ConventPeriodProtocolRepository protocols;
	private final Path baseDir;

	@PersistenceContext
	private EntityManager em;

	public ConventPeriodProtocolService(
			ConventPeriodRepository periods,
			ConventPeriodProtocolRepository protocols,
			@Value("${verhaarm.uploads.period-protocols.dir:/var/lib/verhaarm/uploads/period-protocols}") String baseDir
	) {
		this.periods = periods;
		this.protocols = protocols;
		this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
	}

	@Transactional(readOnly = true)
	public ConventPeriodProtocolDto get(UUID periodId) {
		requirePeriodExists(periodId);

		var protocol = protocols.findByPeriodId(periodId)
				.orElseThrow(() -> ApiErrors.notFound("Protocol not found"));

		return toDto(protocol);
	}

	@Transactional
	public ConventPeriodProtocolDto uploadOrReplace(UUID periodId, MultipartFile file, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		requirePeriodExists(periodId);

		if (file == null || file.isEmpty()) throw ApiErrors.badRequest("File required");
		if (file.getSize() <= 0) throw ApiErrors.badRequest("File empty");
		if (file.getSize() > 20L * 1024 * 1024) throw ApiErrors.badRequest("File too large (max 20MB)");

		String contentType = file.getContentType();
		if (contentType == null || contentType.isBlank()) contentType = "application/octet-stream";

		String original = safeOriginalFilename(file.getOriginalFilename());
		validateLooksLikePdf(original, contentType);

		String stored = safeStoredFilename(UUID.randomUUID() + ".pdf");

		Path periodDir = resolvePeriodDir(periodId);
		Path dest = resolveInPeriodDir(periodDir, stored);

		try {
			Files.createDirectories(periodDir);

			String tmpName = safeStoredFilename(stored + ".tmp");
			Path tmp = resolveInPeriodDir(periodDir, tmpName);

			try (InputStream in = file.getInputStream()) {
				Files.copy(in, tmp, REPLACE_EXISTING);
			}

			validatePdfHeader(tmp);

			try {
				Files.move(tmp, dest, REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(tmp, dest, REPLACE_EXISTING);
			}
		} catch (Exception e) {
			throw ApiErrors.badRequest("Upload failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}

		var existing = protocols.findByPeriodId(periodId).orElse(null);

		if (existing == null) {
			existing = new ConventPeriodProtocolEntity(
					UUID.randomUUID(),
					periodId,
					actor.getId(),
					original,
					stored,
					"application/pdf",
					file.getSize()
			);
		} else {
			deleteStoredFileBestEffort(periodId, existing.getStoredFilename());

			existing.setUploaderUserId(actor.getId());
			existing.setOriginalFilename(original);
			existing.setStoredFilename(stored);
			existing.setContentType("application/pdf");
			existing.setSizeBytes(file.getSize());
		}

		protocols.save(existing);

		em.flush();
		em.clear();

		var reloaded = protocols.findByPeriodId(periodId)
				.orElseThrow(() -> ApiErrors.notFound("Protocol not found"));

		return toDto(reloaded);
	}

	@Transactional(readOnly = true)
	public Download download(UUID periodId) {
		requirePeriodExists(periodId);

		var protocol = protocols.findByPeriodId(periodId)
				.orElseThrow(() -> ApiErrors.notFound("Protocol not found"));

		Path periodDir = resolvePeriodDir(periodId);
		String stored = safeStoredFilename(protocol.getStoredFilename());
		Path file = resolveInPeriodDir(periodDir, stored);

		if (!Files.exists(file)) throw ApiErrors.notFound("File missing on disk");

		return new Download(
				new FileSystemResource(file),
				protocol.getOriginalFilename(),
				protocol.getContentType()
		);
	}

	@Transactional
	public void delete(UUID periodId) {
		requirePeriodExists(periodId);

		var protocol = protocols.findByPeriodId(periodId)
				.orElseThrow(() -> ApiErrors.notFound("Protocol not found"));

		deleteStoredFileBestEffort(periodId, protocol.getStoredFilename());
		protocols.delete(protocol);

		deletePeriodDirectoryBestEffort(periodId);
	}

	@Transactional(readOnly = true)
	public boolean exists(UUID periodId) {
		return protocols.existsByPeriodId(periodId);
	}

	/**
	 * Used by ConventPeriodService when a period is deleted.
	 * DB row is removed by ON DELETE CASCADE; this removes the disk directory.
	 */
	@Transactional(readOnly = true)
	public void deletePeriodDirectoryBestEffort(UUID periodId) {
		Path periodDir = resolvePeriodDir(periodId);
		if (!Files.exists(periodDir)) return;

		try {
			Files.walk(periodDir)
					.sorted(Comparator.reverseOrder())
					.forEach(p -> {
						try { Files.deleteIfExists(p); } catch (Exception ignored) {}
					});
		} catch (Exception ignored) {
		}
	}

	private void requirePeriodExists(UUID periodId) {
		if (!periods.existsById(periodId)) {
			throw ApiErrors.notFound("Period not found");
		}
	}

	private ConventPeriodProtocolDto toDto(ConventPeriodProtocolEntity p) {
		return new ConventPeriodProtocolDto(
				p.getId(),
				p.getPeriodId(),
				p.getUploaderUserId(),
				p.getOriginalFilename(),
				p.getContentType(),
				p.getSizeBytes(),
				p.getCreatedAt(),
				p.getUpdatedAt()
		);
	}

	private static void validateLooksLikePdf(String originalFilename, String contentType) {
		String name = originalFilename.toLowerCase(Locale.ROOT);
		String ct = contentType.toLowerCase(Locale.ROOT);

		boolean filenamePdf = name.endsWith(".pdf");
		boolean contentTypePdf = ct.equals("application/pdf") || ct.equals("application/x-pdf");

		if (!filenamePdf && !contentTypePdf) {
			throw ApiErrors.badRequest("Only PDF uploads are allowed");
		}
	}

	private static void validatePdfHeader(Path file) {
		try (InputStream in = Files.newInputStream(file)) {
			byte[] header = in.readNBytes(5);
			String s = new String(header);
			if (!"%PDF-".equals(s)) {
				throw ApiErrors.badRequest("Invalid PDF file");
			}
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw ApiErrors.badRequest("Could not validate PDF file");
		}
	}

	private void deleteStoredFileBestEffort(UUID periodId, String storedFilename) {
		try {
			Path periodDir = resolvePeriodDir(periodId);
			String stored = safeStoredFilename(storedFilename);
			Path file = resolveInPeriodDir(periodDir, stored);
			Files.deleteIfExists(file);
		} catch (Exception ignored) {
		}
	}

	private static String safeOriginalFilename(String name) {
		String n = (name == null || name.isBlank()) ? "protocol.pdf" : name.trim();

		n = n.replace('\\', '/');
		int slash = n.lastIndexOf('/');
		if (slash >= 0) n = n.substring(slash + 1);

		n = n.replaceAll("[\\x00-\\x1F\\x7F]", "");

		if (n.isBlank()) n = "protocol.pdf";
		if (n.length() > 200) n = n.substring(n.length() - 200);

		if (!n.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
			n = n + ".pdf";
		}

		return n;
	}

	private Path resolvePeriodDir(UUID periodId) {
		Path dir = baseDir.resolve(periodId.toString()).normalize();
		if (!dir.startsWith(baseDir)) {
			throw ApiErrors.badRequest("Invalid period id");
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

		if (!s.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf(\\.tmp)?$")) {
			throw ApiErrors.badRequest("Invalid filename");
		}

		return s;
	}

	private static Path resolveInPeriodDir(Path periodDir, String leafName) {
		Path p = periodDir.resolve(leafName).normalize();
		if (!p.startsWith(periodDir)) {
			throw ApiErrors.badRequest("Invalid path");
		}
		return p;
	}
}