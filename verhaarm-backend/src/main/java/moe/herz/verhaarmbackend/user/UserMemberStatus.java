package moe.herz.verhaarmbackend.user;

public enum UserMemberStatus {
	FUX,
	SCHUELERFUX,
	KONKNEIPANT,
	BURSCH,
	INAKTIVER,
	PHILISTER;

	public boolean isAktivitas() {
		return this != PHILISTER;
	}
}