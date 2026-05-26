package moe.herz.verhaarmbackend.common;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StructuredApiError {
	private StructuredApiError() {}

	public static ApiValidationException badRequest(String code, String message, Map<String, Object> details) {
		return new ApiValidationException(HttpStatus.BAD_REQUEST, code, message, details);
	}

	public static ApiValidationException notFound(String code, String message, Map<String, Object> details) {
		return new ApiValidationException(HttpStatus.NOT_FOUND, code, message, details);
	}

	public static Map<String, Object> details(Object... pairs) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			if (pairs[i] != null) out.put(String.valueOf(pairs[i]), pairs[i + 1]);
		}
		return out;
	}
}
