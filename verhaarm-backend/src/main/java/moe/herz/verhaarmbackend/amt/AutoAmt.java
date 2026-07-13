package moe.herz.verhaarmbackend.amt;

import moe.herz.verhaarmbackend.user.UserRole;

/**
 * The 4 Ämter that are derived live from {@link UserRole} assignments rather than
 * from {@code amt_holders}. Not editable via {@link AmtController} — managed via
 * user administration instead.
 */
public enum AutoAmt {

	SPRECHER("Sprecher", UserRole.SENIOR, 6),
	FECHTWART("Fechtwart", UserRole.FECHTWART, 7),
	SCHMUCKWART("Schmuckwart", UserRole.HOUSEKEEPING, 8),
	KASSENWART("Kassenwart", UserRole.TREASURER, 9);

	private final String label;
	private final UserRole role;
	private final int order;

	AutoAmt(String label, UserRole role, int order) {
		this.label = label;
		this.role = role;
		this.order = order;
	}

	public String label() { return label; }
	public UserRole role() { return role; }
	public int order() { return order; }
}
