package moe.herz.verhaarmbackend.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AttendanceFineConfigRepository extends JpaRepository<AttendanceFineConfigEntity, UUID> {
}
