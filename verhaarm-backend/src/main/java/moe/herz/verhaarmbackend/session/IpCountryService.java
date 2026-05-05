package moe.herz.verhaarmbackend.session;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class IpCountryService {

	private static final Logger log = LoggerFactory.getLogger(IpCountryService.class);

	private final DatabaseReader reader;

	public IpCountryService(
			@Value("${verhaarm.geoip.countryDbPath:}") String countryDbPath
	) {
		this.reader = openReader(countryDbPath);
	}

	public String countryCodeForIp(String ipAddress) {
		if (reader == null) return null;

		InetAddress address = parseAddress(ipAddress);
		if (address == null || isNonPublicAddress(address)) {
			return null;
		}

		try {
			String isoCode = reader.country(address).country().isoCode();
			if (isoCode == null || isoCode.isBlank()) return null;

			String normalized = isoCode.trim().toUpperCase(Locale.ROOT);
			return normalized.matches("[A-Z]{2}") ? normalized : null;
		} catch (AddressNotFoundException e) {
			return null;
		} catch (IOException | GeoIp2Exception e) {
			log.debug("GeoIP country lookup failed for IP {}", ipAddress, e);
			return null;
		}
	}

	public boolean isAvailable() {
		return reader != null;
	}

	@PreDestroy
	public void close() {
		if (reader == null) return;

		try {
			reader.close();
		} catch (IOException e) {
			log.debug("Failed to close GeoIP database reader", e);
		}
	}

	private static DatabaseReader openReader(String countryDbPath) {
		if (countryDbPath == null || countryDbPath.isBlank()) {
			log.info("GeoIP country database path is not configured; session countries will stay empty");
			return null;
		}

		Path path = Path.of(countryDbPath.trim());
		if (!Files.isRegularFile(path)) {
			log.warn("GeoIP country database does not exist or is not a regular file: {}", path);
			return null;
		}

		try {
			return new DatabaseReader.Builder(path.toFile()).build();
		} catch (IOException e) {
			log.warn("Failed to open GeoIP country database: {}", path, e);
			return null;
		}
	}

	private static InetAddress parseAddress(String ipAddress) {
		if (ipAddress == null || ipAddress.isBlank()) return null;

		String cleaned = ipAddress.trim();

		// X-Forwarded-For should already be split by the controller, but keep this safe.
		int comma = cleaned.indexOf(',');
		if (comma >= 0) {
			cleaned = cleaned.substring(0, comma).trim();
		}

		// Strip IPv6 brackets if a value like [2001:db8::1] ever appears.
		if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
			cleaned = cleaned.substring(1, cleaned.length() - 1);
		}

		try {
			return InetAddress.getByName(cleaned);
		} catch (UnknownHostException e) {
			return null;
		}
	}

	private static boolean isNonPublicAddress(InetAddress address) {
		if (address.isAnyLocalAddress()
				|| address.isLoopbackAddress()
				|| address.isLinkLocalAddress()
				|| address.isSiteLocalAddress()
				|| address.isMulticastAddress()) {
			return true;
		}

		byte[] raw = address.getAddress();

		if (raw.length == 4) {
			int b0 = raw[0] & 0xff;
			int b1 = raw[1] & 0xff;

			// 10.0.0.0/8
			if (b0 == 10) return true;

			// 172.16.0.0/12
			if (b0 == 172 && b1 >= 16 && b1 <= 31) return true;

			// 192.168.0.0/16
			if (b0 == 192 && b1 == 168) return true;

			// 100.64.0.0/10 CGNAT
			if (b0 == 100 && b1 >= 64 && b1 <= 127) return true;

			// 127.0.0.0/8
			if (b0 == 127) return true;

			// 169.254.0.0/16
			if (b0 == 169 && b1 == 254) return true;

			// 0.0.0.0/8
			if (b0 == 0) return true;

			// 224.0.0.0/4 multicast and reserved above it
			if (b0 >= 224) return true;
		}

		if (raw.length == 16) {
			int b0 = raw[0] & 0xff;
			int b1 = raw[1] & 0xff;

			// fc00::/7 unique local addresses
			if ((b0 & 0xfe) == 0xfc) return true;

			// fe80::/10 link-local
			if (b0 == 0xfe && (b1 & 0xc0) == 0x80) return true;

			// ::1 loopback
			boolean allZeroExceptLast = true;
			for (int i = 0; i < 15; i++) {
				if (raw[i] != 0) {
					allZeroExceptLast = false;
					break;
				}
			}
			if (allZeroExceptLast && raw[15] == 1) return true;
		}

		return false;
	}
}