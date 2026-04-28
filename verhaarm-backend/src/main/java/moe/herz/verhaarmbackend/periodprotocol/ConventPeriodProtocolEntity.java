package moe.herz.verhaarmbackend.periodprotocol;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "convent_period_protocols")
public class ConventPeriodProtocolEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "period_id", nullable = false)
	private UUID periodId;

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

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	protected ConventPeriodProtocolEntity() {
		// JPA
	}

	public ConventPeriodProtocolEntity(
			UUID id,
			UUID periodId,
			UUID uploaderUserId,
			String originalFilename,
			String storedFilename,
			String contentType,
			long sizeBytes
	) {
		this.id = id;
		this.periodId = periodId;
		this.uploaderUserId = uploaderUserId;
		this.originalFilename = originalFilename;
		this.storedFilename = storedFilename;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
	}

	public UUID getId() { return id; }
	public UUID getPeriodId() { return periodId; }
	public UUID getUploaderUserId() { return uploaderUserId; }
	public String getOriginalFilename() { return originalFilename; }
	public String getStoredFilename() { return storedFilename; }
	public String getContentType() { return contentType; }
	public long getSizeBytes() { return sizeBytes; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }

	public void setUploaderUserId(UUID uploaderUserId) { this.uploaderUserId = uploaderUserId; }
	public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
	public void setStoredFilename(String storedFilename) { this.storedFilename = storedFilename; }
	public void setContentType(String contentType) { this.contentType = contentType; }
	public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
}