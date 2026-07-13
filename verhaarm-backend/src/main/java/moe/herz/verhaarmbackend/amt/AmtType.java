package moe.herz.verhaarmbackend.amt;

/**
 * Fixed catalog of manually-assigned Ämter. The 4 role-derived offices
 * (Sprecher/Fechtwart/Schmuckwart/Kassenwart) live separately in {@link AutoAmt}
 * since they are read from {@link moe.herz.verhaarmbackend.user.UserRole} assignments,
 * not from {@code amt_holders}.
 */
public enum AmtType {

	X("x", Group.EHRENGERICHT, 1),
	XX("xx", Group.EHRENGERICHT, 2),
	XXX("xxx", Group.EHRENGERICHT, 3),
	STELLV_EHRENRICHTER_1("1. stellvertretender Ehrenrichter", Group.EHRENGERICHT, 4),
	STELLV_EHRENRICHTER_2("2. stellvertretender Ehrenrichter", Group.EHRENGERICHT, 5),

	SCHRIFTWART("Schriftwart", Group.OTHER, 10),
	FKD("FKD", Group.OTHER, 11),
	PROTOKOLLWART("Protokollwart", Group.OTHER, 12),
	VERBINDUNGSWART("Verbindungswart", Group.OTHER, 13),
	SK_WART("SK-Wart", Group.OTHER, 14),
	IT_SOCIAL_MEDIA_WART("IT/Social-Media-Wart", Group.OTHER, 15),
	GRILL_UND_KAMINWART("Grill- und Kaminwart", Group.OTHER, 16),
	UHRENFUX("Uhrenfux", Group.OTHER, 17),
	FOTOFUX("Fotofux", Group.OTHER, 18),
	SKD("SKD", Group.OTHER, 19),
	SPORTWART("Sportwart", Group.OTHER, 20);

	public enum Group { EHRENGERICHT, OTHER }

	private final String label;
	private final Group group;
	private final int order;

	AmtType(String label, Group group, int order) {
		this.label = label;
		this.group = group;
		this.order = order;
	}

	public String label() { return label; }
	public Group group() { return group; }
	public int order() { return order; }
	public boolean isEhrengericht() { return group == Group.EHRENGERICHT; }
}
