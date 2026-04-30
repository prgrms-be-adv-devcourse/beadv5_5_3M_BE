package com.example.streamingservice.presentation;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Profile({"dev", "prod"})
@Controller
@RequestMapping("/test/stream")
public class TestStreamPageController {

	@GetMapping
	public String page() {
		return "test/stream";
	}
}