package moe.herz.verhaarmbackend.event;

import jakarta.validation.Valid;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.event.dto.ConventBoardDto;
import moe.herz.verhaarmbackend.event.dto.UpdateConventBoardRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/convents/board")
public class ConventBoardController {

	private final ConventBoardService board;
	private final UserRepository users;

	public ConventBoardController(ConventBoardService board, UserRepository users) {
		this.board = board;
		this.users = users;
	}

	@GetMapping
	public ConventBoardDto get(Authentication auth) {
		return board.board(actor(auth));
	}

	@PostMapping("/validate")
	public ResponseEntity<Void> validate(@RequestBody @Valid UpdateConventBoardRequest req, Authentication auth) {
		board.validateBatch(req, actor(auth));
		return ResponseEntity.noContent().build();
	}

	@PostMapping
	public ConventBoardDto apply(@RequestBody @Valid UpdateConventBoardRequest req, Authentication auth) {
		return board.apply(req, actor(auth));
	}

	private UserEntity actor(Authentication auth) {
		if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
			throw ApiErrors.unauthorized("Unauthorized");
		}
		String username = auth.getName();
		return users.findByUsernameWithRoles(username)
				.orElseThrow(() -> ApiErrors.unauthorized("Unauthorized"));
	}
}
