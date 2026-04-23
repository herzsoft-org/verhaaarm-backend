package moe.herz.verhaarmbackend.finesuggestionphoto;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fine_suggestion_photos")
public class FineSuggestionPhotoEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "suggestion_id", nullable = false)
	private UUID suggestionId;

	@Column(name = "uploader_user_id")
	private UUID uploaderUserId;

	@Column(name = "original_filename", nullable = false)
	private String originalFilename;

	@Column(name = "stored_filename", nullable = false)
	private String storedFilename;

	@Column(name = "content_type", nullable = false)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected FineSuggestionPhotoEntity() {
		// JPA
	}

	public FineSuggestionPhotoEntity(
			UUID id,
			UUID suggestionId,
			UUID uploaderUserId,
			String originalFilename,
			String storedFilename,
			String contentType,
			long sizeBytes
	) {
		this.id = id;
		this.suggestionId = suggestionId;
		this.uploaderUserId = uploaderUserId;
		this.originalFilename = originalFilename;
		this.storedFilename = storedFilename;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
	}

	public UUID getId() { return id; }
	public UUID getSuggestionId() { return suggestionId; }
	public UUID getUploaderUserId() { return uploaderUserId; }
	public String getOriginalFilename() { return originalFilename; }
	public String getStoredFilename() { return storedFilename; }
	public String getContentType() { return contentType; }
	public long getSizeBytes() { return sizeBytes; }
	public OffsetDateTime getDeletedAt() { return deletedAt; }
	public OffsetDateTime getCreatedAt() { return createdAt; }

	public boolean isDeleted() { return deletedAt != null; }
	public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}