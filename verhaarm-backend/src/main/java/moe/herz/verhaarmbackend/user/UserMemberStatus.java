package moe.herz.verhaarmbackend.user;

public enum UserMemberStatus {
	FUX,
	SCHUELERFUX,
	MILITAERFUX,
	BURSCH,
	INAKTIVER,
	PHILISTER;

	public boolean isAktivitas() {
		return this != PHILISTER;
	}
}