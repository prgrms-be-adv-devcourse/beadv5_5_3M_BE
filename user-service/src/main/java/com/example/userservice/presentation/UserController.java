package com.example.userservice.presentation;

import com.example.userservice.application.UserUseCase;
import com.example.userservice.presentation.dto.req.AuthorizationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserUseCase userUseCase;

    @GetMapping("/authorization/check")
    public ResponseEntity<Boolean> check(@ModelAttribute AuthorizationRequest request,
                                         @RequestHeader("X-User-Id") String userId) {

        return ResponseEntity.ok(userUseCase.checkAuthorization(request, userId));
    }
}
