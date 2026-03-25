package com.example.userservice.presentation;

import com.example.userservice.application.UserUseCase;
import com.example.userservice.presentation.dto.req.AuthorizationRequest;
import com.example.userservice.presentation.dto.req.JoinRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @GetMapping("/email/check")
    public ResponseEntity<Void> checkEmail(@RequestParam String email) {
        userUseCase.checkEmailDuplicate(email);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/nickname/check")
    public ResponseEntity<Void> checkNickname(@RequestParam String nickname) {
        userUseCase.checkNicknameDuplicate(nickname);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/join")
    public ResponseEntity<UUID> join(@Valid @RequestBody JoinRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userUseCase.join(request));
    }
}
