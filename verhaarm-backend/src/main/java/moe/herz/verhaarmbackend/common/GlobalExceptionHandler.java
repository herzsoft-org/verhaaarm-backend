package moe.herz.verhaarmbackend.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<?> handleResponseStatus(ResponseStatusException ex) {
		return ResponseEntity
				.status(ex.getStatusCode())
				.body(Map.of(
						"error", ex.getReason()
				));
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

	/**
	 * Covers cases like:
	 *  - /fine-catalog/<ID> where <ID> is not a UUID
	 *  - invalid query param types
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return ResponseEntity.badRequest().body(Map.of(
				"error", "Bad Request",
				"details", ex.getName() + ": invalid value"
		));
	}

	/**
	 * Covers malformed JSON bodies.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<?> handleBadJson(HttpMessageNotReadableException ex) {
		return ResponseEntity.badRequest().body(Map.of(
				"error", "Bad Request",
				"details", "Malformed JSON"
		));
	}
}
