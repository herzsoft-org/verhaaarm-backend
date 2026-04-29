package moe.herz.verhaarmbackend.user;

public enum UserMemberStatus {
	FUX,
	BURSCH,
	INAKTIVER,
	PHILISTER;

	public boolean isAktivitas() {
		return this != PHILISTER;
	}
}