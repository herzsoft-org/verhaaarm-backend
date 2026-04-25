package moe.herz.verhaarmbackend.session.dto;

public record SessionDeviceInfoRequest(
		String appType,          // WEB, ANDROID, or omitted/unknown
		String deviceName,
		String deviceModel,
		String osName,
		String osVersion,
		String browserName,
		String browserVersion,
		String userAgent
) {}