package moe.herz.verhaarmbackend.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ApiErrors {
	private ApiErrors() {}

	public static ResponseStatusException badRequest(String msg) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
	}

	public static ResponseStatusException unauthorized(String msg) {
		return new ResponseStatusException(HttpStatus.UNAUTHORIZED, msg);
	}

	public static ResponseStatusException forbidden(String msg) {
		return new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
	}

	public static ResponseStatusException notFound(String msg) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
	}
}
