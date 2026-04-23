package com.example.streamingservice.infrastructure.websocket;

import java.security.Principal;

public record StreamingPrincipal(String name) implements Principal {

	@Override
	public String getName() {
		return name;
	}
}