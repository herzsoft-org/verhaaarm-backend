package moe.herz.verhaarmbackend.push;

public class WebPushSendException extends RuntimeException {

	private final int statusCode;
	private final String responseBody;

	public WebPushSendException(int statusCode, String responseBody) {
		super("WebPush failed: HTTP " + statusCode + " body=" + responseBody);
		this.statusCode = statusCode;
		this.responseBody = responseBody;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public String getResponseBody() {
		return responseBody;
	}
}