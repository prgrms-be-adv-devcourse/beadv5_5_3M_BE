package com.example.streamingservice.application.usecase;

import com.example.streamingservice.application.dto.IssueSessionCommand;
import com.example.streamingservice.application.dto.SessionIssueResult;

public interface EnterStreamUseCase {

	SessionIssueResult issue(IssueSessionCommand command);
}