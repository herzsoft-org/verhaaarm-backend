package moe.herz.verhaarmbackend.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private final boolean debugErrors;

	public GlobalExceptionHandler(@Value("${verhaarm.debugErrors:false}") boolean debugErrors) {
		this.debugErrors = debugErrors;
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<?> handleMaxUpload(MaxUploadSizeExceededException ex) {
		if (debugErrors) {
			return ResponseEntity.status(413).body(Map.of(
					"error", "Payload Too Large",
					"details", ex.getClass().getSimpleName() + ": " + ex.getMessage()
			));
		}
		return ResponseEntity.status(413).body(Map.of(
				"error", "Payload Too Large",
				"details", "Upload too large"
		));
	}

	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<?> handleMultipart(MultipartException ex) {
		if (debugErrors) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
					"error", "Bad Request",
					"details", "Multipart error: " + ex.getMessage()
			));
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
				"error", "Bad Request",
				"details", "Multipart error"
		));
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<?> handleResponseStatus(ResponseStatusException ex) {
		final String reason = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();
		return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", reason));
	}

	@ExceptionHandler(ApiValidationException.class)
	public ResponseEntity<?> handleApiValidation(ApiValidationException ex) {
		Map<String, Object> body = new java.util.LinkedHashMap<>();
		body.put("error", ex.getMessage());
		body.put("code", ex.getCode());
		body.putAll(ex.getDetails());
		return ResponseEntity.status(ex.getStatus()).body(body);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
		return ResponseEntity.badRequest().body(Map.of(
				"error", "Validation failed",
				"details", ex.getBindingResult().getFieldErrors().stream()
						.map(e -> e.getField() + ": " + e.getDefaultMessage())
						.toList()
		));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return ResponseEntity.badRequest().body(Map.of(
				"error", "Bad Request",
				"details", ex.getName() + ": invalid value"
		));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<?> handleBadJson(HttpMessageNotReadableException ex) {
		if (debugErrors) {
			return ResponseEntity.badRequest().body(Map.of(
					"error", "Bad Request",
					"details", "Malformed JSON: " + ex.getMostSpecificCause().getMessage()
			));
		}
		return ResponseEntity.badRequest().body(Map.of(
				"error", "Bad Request",
				"details", "Malformed JSON"
		));
	}

	// IMPORTANT: without this, controllers throwing IllegalArgumentException can become a 500.
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
		if (debugErrors) {
			return ResponseEntity.badRequest().body(Map.of(
					"error", "Bad Request",
					"details", ex.getClass().getSimpleName() + ": " + ex.getMessage()
			));
		}
		return ResponseEntity.badRequest().body(Map.of(
				"error", "Bad Request",
				"details", "Bad Request"
		));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException ex) {
		if (debugErrors) {
			final Throwable cause = ex.getMostSpecificCause();
			final String msg = cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
					"error", "Conflict",
					"details", msg != null ? msg : "Data integrity violation"
			));
		}
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Conflict"));
	}

	@ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
	public ResponseEntity<?> handleAccessDenied(Exception ex) {
		if (debugErrors) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
					"error", "Forbidden",
					"details", ex.getClass().getSimpleName() + ": " + ex.getMessage()
			));
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<?> handleGeneric(Exception ex) {
		if (debugErrors) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
					"error", "Internal Server Error",
					"details", ex.getClass().getSimpleName() + ": " + ex.getMessage()
			));
		}
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal Server Error"));
	}
}
