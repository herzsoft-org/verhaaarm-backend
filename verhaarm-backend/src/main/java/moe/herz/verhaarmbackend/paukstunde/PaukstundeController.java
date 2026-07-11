package moe.herz.verhaarmbackend.paukstunde;

import jakarta.validation.Valid;
import moe.herz.verhaarmbackend.paukstunde.dto.CreatePaukstundeRequest;
import moe.herz.verhaarmbackend.paukstunde.dto.PaukstundeDto;
import moe.herz.verhaarmbackend.paukstunde.dto.PaukstundeUserTotalDto;
import moe.herz.verhaarmbackend.paukstunde.dto.UpdatePaukstundeRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/paukstunden")
public class PaukstundeController {
	private final PaukstundeService paukstunden;
	private final UserRepository users;

	public PaukstundeController(PaukstundeService paukstunden, UserRepository users) {
		this.paukstunden = paukstunden;
		this.users = users;
	}

	@PostMapping
	public PaukstundeDto create(@RequestBody @Valid CreatePaukstundeRequest req, Authentication auth) {
		return paukstunden.create(req, actor(auth));
	}

	@GetMapping("/current-conventsperiode")
	public List<PaukstundeDto> listCurrentConventsperiode(Authentication auth) {
		return paukstunden.listCurrentConventsperiode(actor(auth));
	}

	@GetMapping("/me/current-conventsperiode")
	public PaukstundeUserTotalDto myCurrentTotal(Authentication auth) {
		return paukstunden.myCurrentTotal(actor(auth));
	}

	@GetMapping("/users/{userId}/current-conventsperiode")
	public PaukstundeUserTotalDto userCurrentTotal(@PathVariable UUID userId, Authentication auth) {
		return paukstunden.userCurrentTotal(userId, actor(auth));
	}

	@GetMapping("/summary/current-conventsperiode")
	public List<PaukstundeUserTotalDto> summaryCurrentConventsperiode(Authentication auth) {
		return paukstunden.summaryCurrentConventsperiode(actor(auth));
	}

	@GetMapping("/conventsperiode/{periodId}")
	public List<PaukstundeDto> listForConventsperiode(@PathVariable UUID periodId, Authentication auth) {
		return paukstunden.listForConventsperiode(periodId, actor(auth));
	}

	@GetMapping("/summary/conventsperiode/{periodId}")
	public List<PaukstundeUserTotalDto> summaryForConventsperiode(@PathVariable UUID periodId, Authentication auth) {
		return paukstunden.summaryForConventsperiode(periodId, actor(auth));
	}

	@PatchMapping("/{id}")
	public PaukstundeDto update(@PathVariable UUID id, @RequestBody UpdatePaukstundeRequest req, Authentication auth) {
		return paukstunden.update(id, req, actor(auth));
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable UUID id, Authentication auth) {
		paukstunden.delete(id, actor(auth));
	}

	private UserEntity actor(Authentication auth) {
		String username = auth.getName();
		return users.findByUsernameWithRoles(username)
				.orElseThrow(() -> moe.herz.verhaarmbackend.common.ApiErrors.unauthorized("Unauthorized"));
	}
}
