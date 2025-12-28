package moe.herz.verhaarmbackend.quotes;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuotesController {

	private final QuotesService quotes;

	public QuotesController(QuotesService quotes) {
		this.quotes = quotes;
	}

	@GetMapping(value = "/public/quotes", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getQuotesJson() {
		final String json = quotes.getQuotesJson();
		if (json == null || json.isBlank()) {
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.ok(json);
	}
}
