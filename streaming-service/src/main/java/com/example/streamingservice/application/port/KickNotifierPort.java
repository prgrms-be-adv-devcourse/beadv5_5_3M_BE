package com.example.streamingservice.application.port;

import java.util.UUID;

import com.example.streamingservice.domain.SessionKickReason;

public interface KickNotifierPort {

	void notify(UUID userId, SessionKickReason reason);

	void forceClose(UUID userId);
}