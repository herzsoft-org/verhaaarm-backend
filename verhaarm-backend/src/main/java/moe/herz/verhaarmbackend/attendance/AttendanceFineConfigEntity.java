package moe.herz.verhaarmbackend.attendance;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance_fine_config")
public class AttendanceFineConfigEntity {

	@Id
	@Column(name = "config_id", nullable = false)
	private short configId;

	@Column(name = "late_catalog_item_id")
	private UUID lateCatalogItemId;

	@Column(name = "late_reason")
	private String lateReason;

	@Column(name = "late_amount_cents")
	private Integer lateAmountCents;

	@Column(name = "absent_catalog_item_id")
	private UUID absentCatalogItemId;

	@Column(name = "absent_reason")
	private String absentReason;

	@Column(name = "absent_amount_cents")
	private Integer absentAmountCents;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	protected AttendanceFineConfigEntity() {
		// JPA
	}

	public AttendanceFineConfigEntity(short configId) {
		this.configId = configId;
	}

	public short getConfigId() { return configId; }

	public UUID getLateCatalogItemId() { return lateCatalogItemId; }
	public String getLateReason() { return lateReason; }
	public Integer getLateAmountCents() { return lateAmountCents; }

	public UUID getAbsentCatalogItemId() { return absentCatalogItemId; }
	public String getAbsentReason() { return absentReason; }
	public Integer getAbsentAmountCents() { return absentAmountCents; }

	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }

	public void setLateCatalogItemId(UUID lateCatalogItemId) { this.lateCatalogItemId = lateCatalogItemId; }
	public void setLateReason(String lateReason) { this.lateReason = lateReason; }
	public void setLateAmountCents(Integer lateAmountCents) { this.lateAmountCents = lateAmountCents; }

	public void setAbsentCatalogItemId(UUID absentCatalogItemId) { this.absentCatalogItemId = absentCatalogItemId; }
	public void setAbsentReason(String absentReason) { this.absentReason = absentReason; }
	public void setAbsentAmountCents(Integer absentAmountCents) { this.absentAmountCents = absentAmountCents; }
}
