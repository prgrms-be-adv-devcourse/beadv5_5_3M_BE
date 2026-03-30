package com.example.creatorservice.presentation;

import com.example.creatorservice.application.usecase.CreatorUseCase;
import com.example.creatorservice.presentation.dto.req.AuthorizationRequest;
import com.example.creatorservice.presentation.dto.req.JoinRequest;
import com.example.creatorservice.presentation.dto.req.LoginRequest;
import com.example.creatorservice.presentation.dto.res.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/creators")
@RequiredArgsConstructor
public class CreatorController {

    private final CreatorUseCase creatorUseCase;

    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UUID> save(@RequestBody JoinRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(creatorUseCase.join(request));
    }

    @GetMapping("/email/check")
    public ResponseEntity<Void> checkEmail(@RequestParam String email) {
        creatorUseCase.checkEmailDuplicate(email);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/nickname/check")
    public ResponseEntity<Void> checkNickname(@RequestParam String nickname) {
        creatorUseCase.checkNicknameDuplicate(nickname);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        TokenResponse response = creatorUseCase.login(request);

        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/authorization/check")
    public ResponseEntity<Boolean> check(@ModelAttribute AuthorizationRequest request,
                                         @RequestHeader("X-Creator-Id") String creatorId) {
        return ResponseEntity.ok(creatorUseCase.checkAuthorization(request, creatorId));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody String refreshToken) {
        return ResponseEntity.status(HttpStatus.CREATED).body(creatorUseCase.refresh(refreshToken));
    }






//    @GetMapping("/{creatorId}")
//    public CreatorResponse getCreator(@PathVariable UUID creatorId) {
//        return getCreatorUseCase.getCreator(creatorId);
//    }
//
//    @DeleteMapping("/{creatorId}")
//    @ResponseStatus(HttpStatus.NO_CONTENT)
//    public void deleteCreator(@PathVariable UUID creatorId) {
//        deleteCreatorUseCase.deleteCreator(creatorId);
//    }
//
//    @PostMapping("/{creatorId}/account")
//    public CreatorResponse registerAccount(
//            @PathVariable UUID creatorId,
//            @RequestBody @Valid RegisterAccountRequest request) {
//        return registerCreatorAccountUseCase.registerAccount(creatorId, request);
//    }
//
//    @PutMapping("/{creatorId}/account")
//    public CreatorResponse updateAccount(
//            @PathVariable UUID creatorId,
//            @RequestBody @Valid UpdateAccountRequest request) {
//        return updateCreatorAccountUseCase.updateAccount(creatorId, request);
//    }
}