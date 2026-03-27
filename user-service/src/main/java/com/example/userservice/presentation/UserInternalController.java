package com.example.userservice.presentation;

import com.example.userservice.application.UserService;
import com.example.userservice.presentation.dto.req.DeductCookieRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class UserInternalController {

    private final UserService userService;

    @PostMapping("/deduct/cookie")
    public ResponseEntity<?> deductCookie(@RequestBody DeductCookieRequest request) {
        return ResponseEntity.ok().body(userService.deductCookie(request));
    }

}
