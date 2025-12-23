package moe.herz.verhaarmbackend.audit;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, updatable = false)
	private Long id;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "actor_user_id")
	private UUID actorUserId;

	@Column(name = "action", nullable = false)
	private String action;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "details", nullable = false, columnDefinition = "jsonb")
	private JsonNode details;

	protected AuditLogEntity() {
		// JPA
	}

	public AuditLogEntity(UUID actorUserId, String action, JsonNode details) {
		this.actorUserId = actorUserId;
		this.action = Objects.requireNonNull(action, "action");
		this.details = Objects.requireNonNull(details, "details");
	}

	@PrePersist
	void prePersist() {
		if (createdAt == null) createdAt = OffsetDateTime.now();
	}

	public Long getId() { return id; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public UUID getActorUserId() { return actorUserId; }
	public String getAction() { return action; }
	public JsonNode getDetails() { return details; }
}
