package com.example.streamingservice.application.usecase;

import com.example.streamingservice.application.dto.SendChatCommand;

public interface ChatUseCase {

	void send(SendChatCommand command);
}