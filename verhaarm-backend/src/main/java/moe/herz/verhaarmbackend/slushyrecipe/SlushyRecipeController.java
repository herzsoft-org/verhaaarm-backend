package moe.herz.verhaarmbackend.slushyrecipe;

import jakarta.validation.Valid;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.slushyrecipe.dto.CreateSlushyRecipeRequest;
import moe.herz.verhaarmbackend.slushyrecipe.dto.RateSlushyRecipeRequest;
import moe.herz.verhaarmbackend.slushyrecipe.dto.SlushyRecipeDto;
import moe.herz.verhaarmbackend.slushyrecipe.dto.UpdateSlushyRecipeRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/slushy-recipes")
public class SlushyRecipeController {

	private final SlushyRecipeService slushyRecipes;
	private final UserRepository users;

	public SlushyRecipeController(SlushyRecipeService slushyRecipes, UserRepository users) {
		this.slushyRecipes = slushyRecipes;
		this.users = users;
	}

	@PostMapping
	public SlushyRecipeDto create(@RequestBody @Valid CreateSlushyRecipeRequest req, Authentication auth) {
		return slushyRecipes.create(req, actor(auth));
	}

	@GetMapping
	public List<SlushyRecipeDto> list(Authentication auth) {
		return slushyRecipes.list(actor(auth));
	}

	@GetMapping("/{id}")
	public SlushyRecipeDto get(@PathVariable UUID id, Authentication auth) {
		return slushyRecipes.get(id, actor(auth));
	}

	@PatchMapping("/{id}")
	public SlushyRecipeDto update(@PathVariable UUID id, @RequestBody UpdateSlushyRecipeRequest req, Authentication auth) {
		return slushyRecipes.update(id, req, actor(auth));
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable UUID id, Authentication auth) {
		slushyRecipes.delete(id, actor(auth));
	}

	@PutMapping("/{id}/ratings")
	public SlushyRecipeDto rate(@PathVariable UUID id, @RequestBody @Valid RateSlushyRecipeRequest req, Authentication auth) {
		return slushyRecipes.rate(id, req, actor(auth));
	}

	private UserEntity actor(Authentication auth) {
		return users.findByUsernameWithRoles(auth.getName())
				.orElseThrow(() -> ApiErrors.unauthorized("Unauthorized"));
	}
}
