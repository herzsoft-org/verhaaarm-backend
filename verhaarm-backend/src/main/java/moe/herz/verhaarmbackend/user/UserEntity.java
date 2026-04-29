package moe.herz.verhaarmbackend.user;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
public class UserEntity {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "username", nullable = false, unique = true)
	private String username;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "username_normalized", nullable = false)
	private String usernameNormalized;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "disabled", nullable = false)
	private boolean disabled;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@Column(name = "last_online_at")
	private OffsetDateTime lastOnlineAt;

	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private Set<UserRoleEntity> roles = new HashSet<>();

	protected UserEntity() {
		// JPA
	}

	public UserEntity(UUID id, String username, String displayName, String passwordHash, boolean disabled) {
		this.id = id;
		this.username = username;
		this.displayName = displayName;
		this.usernameNormalized = UsernameNormalizer.normalize(username);
		this.passwordHash = passwordHash;
		this.disabled = disabled;
	}

	@PrePersist
	void prePersist() {
		if (id == null) id = UUID.randomUUID();
		if (usernameNormalized == null || usernameNormalized.isBlank()) {
			usernameNormalized = UsernameNormalizer.normalize(username);
		}
		if (memberStatus == null) memberStatus = UserMemberStatus.BURSCH;
		if (createdAt == null) createdAt = OffsetDateTime.now();
		if (updatedAt == null) updatedAt = OffsetDateTime.now();
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = OffsetDateTime.now();
		usernameNormalized = UsernameNormalizer.normalize(username);
	}

	@Enumerated(EnumType.STRING)
	@Column(name = "member_status", nullable = false)
	private UserMemberStatus memberStatus = UserMemberStatus.BURSCH;

	// --- Role helpers

	public void clearRoles() {
		roles.clear();
	}

	public void addRole(UserRole role) {
		roles.add(new UserRoleEntity(this, role));
	}

	public boolean hasRole(UserRole role) {
		return roles.stream().anyMatch(r -> r.getRole() == role);
	}

	public Set<UserRole> roleSet() {
		return roles.stream().map(UserRoleEntity::getRole).collect(Collectors.toSet());
	}

	// --- Getters / setters

	public UUID getId() { return id; }

	public String getUsername() { return username; }
	public void setUsername(String username) {
		this.username = username;
		this.usernameNormalized = UsernameNormalizer.normalize(username);
	}

	public String getDisplayName() { return displayName; }
	public void setDisplayName(String displayName) { this.displayName = displayName; }

	public String getUsernameNormalized() { return usernameNormalized; }

	public String getPasswordHash() { return passwordHash; }
	public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

	public boolean isDisabled() { return disabled; }
	public void setDisabled(boolean disabled) { this.disabled = disabled; }

	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }

	public Set<UserRoleEntity> getRoles() { return roles; }

	public OffsetDateTime getLastOnlineAt() { return lastOnlineAt; }
	public void setLastOnlineAt(OffsetDateTime lastOnlineAt) { this.lastOnlineAt = lastOnlineAt; }

	public UserMemberStatus getMemberStatus() {
		return memberStatus;
	}

	public void setMemberStatus(UserMemberStatus memberStatus) {
		this.memberStatus = memberStatus == null ? UserMemberStatus.BURSCH : memberStatus;
	}

	public boolean isAktivitas() {
		return memberStatus == null || memberStatus.isAktivitas();
	}


}
