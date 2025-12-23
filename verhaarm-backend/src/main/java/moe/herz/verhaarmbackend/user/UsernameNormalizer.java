package moe.herz.verhaarmbackend.user;

import java.text.Normalizer;
import java.util.Locale;

public final class UsernameNormalizer {
	private UsernameNormalizer() {}

	/**
	 * Rules (from your spec):
	 * - lowercase
	 * - ä→ae ö→oe ü→ue ß→ss
	 * - only [a-z0-9-]
	 * - no separate first/last storage; this is used for sorting/search
	 */
	public static String normalize(String input) {
		if (input == null) return "";

		String s = input.trim().toLowerCase(Locale.ROOT);

		// German transliterations first (before removing diacritics)
		s = s.replace("ä", "ae")
				.replace("ö", "oe")
				.replace("ü", "ue")
				.replace("ß", "ss");

		// Normalize any remaining weird unicode into a stable form
		s = Normalizer.normalize(s, Normalizer.Form.NFKD);

		// Drop combining marks (accents)
		s = s.replaceAll("\\p{M}+", "");

		// Keep only a-z, 0-9 and '-'
		s = s.replaceAll("[^a-z0-9-]", "");

		// Collapse repeated dashes
		s = s.replaceAll("-{2,}", "-");

		// Trim dashes
		s = s.replaceAll("^-+", "").replaceAll("-+$", "");

		return s;
	}
}
