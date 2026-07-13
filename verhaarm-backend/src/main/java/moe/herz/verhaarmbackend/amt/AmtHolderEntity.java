package moe.herz.verhaarmbackend.amt;

import jakarta.persistence.*;
import moe.herz.verhaarmbackend.user.UserEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "amt_holders")
public class AmtHolderEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "amt_type", nullable = false, length = 64)
	private AmtType amtType;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected AmtHolderEntity() {
		// JPA
	}

	public AmtHolderEntity(UUID id, AmtType amtType, UserEntity user) {
		this.id = id;
		this.amtType = amtType;
		this.user = user;
	}

	public UUID getId() { return id; }
	public AmtType getAmtType() { return amtType; }
	public UserEntity getUser() { return user; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
}
