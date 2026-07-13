package moe.herz.verhaarmbackend.amt;

import jakarta.validation.Valid;
import moe.herz.verhaarmbackend.amt.dto.AemterOverviewDto;
import moe.herz.verhaarmbackend.amt.dto.AmtEntryDto;
import moe.herz.verhaarmbackend.amt.dto.SetAmtHoldersRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/amt")
public class AmtController {

	private final AmtService amt;
	private final UserRepository userRepo;

	public AmtController(AmtService amt, UserRepository userRepo) {
		this.amt = amt;
		this.userRepo = userRepo;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public AemterOverviewDto overview() {
		return amt.getOverview();
	}

	// Any user currently holding some Amt (auto or manual) may edit; enforced in AmtService
	// since it needs the actor plus the full holder picture, not a static role.
	@PatchMapping("/{amtType}/holders")
	@PreAuthorize("isAuthenticated()")
	public AmtEntryDto setHolders(
			@PathVariable AmtType amtType,
			@Valid @RequestBody SetAmtHoldersRequest req,
			Authentication auth
	) {
		UserEntity actor = resolveActor(auth);
		return amt.setHolders(amtType, req.userIds(), actor);
	}

	private UserEntity resolveActor(Authentication auth) {
		if (auth == null || auth.getName() == null || auth.getName().isBlank()) return null;
		return userRepo.findByUsername(auth.getName()).orElse(null);
	}
}
