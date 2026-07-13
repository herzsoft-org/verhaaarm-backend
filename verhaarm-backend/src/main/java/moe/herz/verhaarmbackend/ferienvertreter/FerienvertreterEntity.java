package moe.herz.verhaarmbackend.ferienvertreter;

import jakarta.persistence.*;
import moe.herz.verhaarmbackend.user.UserEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ferienvertreter")
public class FerienvertreterEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@Column(name = "from_date", nullable = false)
	private LocalDate fromDate;

	@Column(name = "until_date", nullable = false)
	private LocalDate untilDate;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	protected FerienvertreterEntity() {
		// JPA
	}

	public FerienvertreterEntity(UUID id, UserEntity user, LocalDate fromDate, LocalDate untilDate) {
		this.id = id;
		this.user = user;
		this.fromDate = fromDate;
		this.untilDate = untilDate;
	}

	public UUID getId() { return id; }

	public UserEntity getUser() { return user; }
	public void setUser(UserEntity user) { this.user = user; }

	public LocalDate getFromDate() { return fromDate; }
	public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }

	public LocalDate getUntilDate() { return untilDate; }
	public void setUntilDate(LocalDate untilDate) { this.untilDate = untilDate; }

	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
