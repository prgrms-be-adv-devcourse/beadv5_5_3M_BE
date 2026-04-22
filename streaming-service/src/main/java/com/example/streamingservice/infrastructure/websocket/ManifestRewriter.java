package com.example.streamingservice.infrastructure.websocket;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ManifestRewriter {

	private static final int MAX_MANIFEST_BYTES = 1_048_576;

	private ManifestRewriter() {
	}

	public static String rewrite(String manifest, long scheduleId, String token, String publicBaseUrl) {
		if (manifest == null) {
			return "";
		}
		if (manifest.length() > MAX_MANIFEST_BYTES) {
			throw new IllegalStateException("manifest exceeds 1MB limit");
		}

		String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
		String prefix = publicBaseUrl + "/api/streaming/" + scheduleId + "/";
		StringBuilder out = new StringBuilder(manifest.length() + 256);
		String[] lines = manifest.split("\\R", -1);

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			if (i > 0) {
				out.append('\n');
			}
			if (line.isEmpty() || line.startsWith("#")) {
				out.append(line);
				continue;
			}
			int slash = line.lastIndexOf('/');
			String fileName = slash < 0 ? line : line.substring(slash + 1);
			out.append(prefix).append(fileName).append("?t=").append(encodedToken);
		}
		return out.toString();
	}
}