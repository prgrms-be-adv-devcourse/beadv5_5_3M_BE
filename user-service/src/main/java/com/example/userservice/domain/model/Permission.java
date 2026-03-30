package com.example.userservice.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "역할별 접근 권한 엔티티")
@Entity
@Getter
@Table(name = "permissions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Permission {

    @Schema(description = "권한 ID")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "유저 역할", example = "USER")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Schema(description = "HTTP 메서드 (null이면 전체 허용)", example = "GET")
    private String httpMethod;

    @Schema(description = "허용된 경로 패턴", example = "/api/users/**")
    @Column(nullable = false)
    private String pathPattern;
}
