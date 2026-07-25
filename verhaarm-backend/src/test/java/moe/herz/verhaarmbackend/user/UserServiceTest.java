package moe.herz.verhaarmbackend.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import moe.herz.verhaarmbackend.attendance.AttendanceRepository;
import moe.herz.verhaarmbackend.audit.AuditLogRepository;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiValidationException;
import moe.herz.verhaarmbackend.fine.FineRepository;
import moe.herz.verhaarmbackend.finephoto.FinePhotoService;
import moe.herz.verhaarmbackend.paukstunde.PaukstundeRepository;
import moe.herz.verhaarmbackend.period.ConventPeriodService;
import moe.herz.verhaarmbackend.push.PushDeviceRepository;
import moe.herz.verhaarmbackend.auth.RefreshTokenRepository;
import moe.herz.verhaarmbackend.task.TaskAssigneeRepository;
import moe.herz.verhaarmbackend.task.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	private final UserRepository users = mock(UserRepository.class);
	private final AuditLogRepository auditRepo = mock(AuditLogRepository.class);
	private final AuditLogService audit = new AuditLogService(auditRepo, new ObjectMapper());

	private final UserService service = new UserService(
			users,
			mock(FineRepository.class),
			mock(ConventPeriodService.class),
			mock(PasswordEncoder.class),
			audit,
			mock(RefreshTokenRepository.class),
			mock(PushDeviceRepository.class),
			mock(TaskRepository.class),
			mock(TaskAssigneeRepository.class),
			mock(AttendanceRepository.class),
			mock(UserRoleRepository.class),
			mock(FinePhotoService.class),
			mock(PaukstundeRepository.class)
	);

	@Test
	void setRoleHoldersMovesRoleFromOldToNewHolder() {
		UserEntity oldHolder = user();
		oldHolder.addRole(UserRole.FECHTWART);
		UserEntity newHolder = user();

		when(users.findAllEnabledByRole(UserRole.FECHTWART)).thenReturn(List.of(oldHolder));
		when(users.findAllById(List.of(newHolder.getId()))).thenReturn(List.of(newHolder));

		List<UserEntity> result = service.setRoleHolders(UserRole.FECHTWART, List.of(newHolder.getId()), oldHolder);

		assertFalse(oldHolder.hasRole(UserRole.FECHTWART));
		assertTrue(newHolder.hasRole(UserRole.FECHTWART));
		assertEquals(1, result.size());
		assertEquals(newHolder.getId(), result.getFirst().getId());
	}

	@Test
	void setRoleHoldersRejectsEmptyHolderList() {
		assertThrows(ApiValidationException.class,
				() -> service.setRoleHolders(UserRole.TREASURER, List.of(), null));
	}

	@Test
	void setRoleHoldersRejectsAdminRole() {
		assertThrows(ResponseStatusException.class,
				() -> service.setRoleHolders(UserRole.ADMIN, List.of(UUID.randomUUID()), null));
	}

	@Test
	void setRoleHoldersKeepsUserWhoAlreadyHadTheRole() {
		UserEntity holder = user();
		holder.addRole(UserRole.SENIOR);

		when(users.findAllEnabledByRole(UserRole.SENIOR)).thenReturn(List.of(holder));
		when(users.findAllById(List.of(holder.getId()))).thenReturn(List.of(holder));

		List<UserEntity> result = service.setRoleHolders(UserRole.SENIOR, List.of(holder.getId()), holder);

		assertEquals(1, holder.getRoles().stream().filter(r -> r.getRole() == UserRole.SENIOR).count());
		assertEquals(1, result.size());
	}

	private static UserEntity user() {
		UUID id = UUID.randomUUID();
		return new UserEntity(id, "user-" + id, "User " + id, "hash", false);
	}
}
