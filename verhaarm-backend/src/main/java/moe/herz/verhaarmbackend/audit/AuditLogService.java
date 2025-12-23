package moe.herz.verhaarmbackend.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import moe.herz.verhaarmbackend.user.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

@Service
public class AuditLogService {

	private final AuditLogRepository repo;
	private final ObjectMapper om;

	public AuditLogService(AuditLogRepository repo, ObjectMapper om) {
		this.repo = repo;
		this.om = om;
	}

	@Transactional
	public void log(UUID actorUserId, String action, JsonNode details) {
		repo.save(new AuditLogEntity(actorUserId, action, details));
	}

	@Transactional
	public void log(UserEntity actor, String action, JsonNode details) {
		UUID actorId = (actor == null) ? null : actor.getId();
		log(actorId, action, details);
	}

	public ObjectNode obj() {
		return om.createObjectNode();
	}

	public ObjectNode put(ObjectNode o, String k, UUID v) {
		if (v == null) o.putNull(k); else o.put(k, v.toString());
		return o;
	}

	public ObjectNode put(ObjectNode o, String k, String v) {
		if (v == null) o.putNull(k); else o.put(k, v);
		return o;
	}

	public ObjectNode put(ObjectNode o, String k, Integer v) {
		if (v == null) o.putNull(k); else o.put(k, v);
		return o;
	}

	public ObjectNode put(ObjectNode o, String k, Long v) {
		if (v == null) o.putNull(k); else o.put(k, v);
		return o;
	}

	public ObjectNode put(ObjectNode o, String k, Boolean v) {
		if (v == null) o.putNull(k); else o.put(k, v);
		return o;
	}

	public ObjectNode putUuidArray(ObjectNode o, String k, Collection<UUID> ids) {
		var arr = om.createArrayNode();
		if (ids != null) {
			for (UUID id : ids) arr.add(id.toString());
		}
		o.set(k, arr);
		return o;
	}

	public ObjectNode putStringArray(ObjectNode o, String k, Collection<String> values) {
		var arr = om.createArrayNode();
		if (values != null) {
			for (String v : values) arr.add(v);
		}
		o.set(k, arr);
		return o;
	}
}
