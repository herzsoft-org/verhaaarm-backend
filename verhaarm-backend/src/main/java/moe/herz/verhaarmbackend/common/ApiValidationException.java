package moe.herz.verhaarmbackend.common;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApiValidationException extends RuntimeException {
	private final HttpStatus status;
	private final String code;
	private final Map<String, Object> details;

	public ApiValidationException(HttpStatus status, String code, String message, Map<String, Object> details) {
		super(message);
		this.status = status;
		this.code = code;
		this.details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public Map<String, Object> getDetails() {
		return details;
	}
}
