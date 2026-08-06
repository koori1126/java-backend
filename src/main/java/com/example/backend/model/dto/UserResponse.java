package com.example.backend.model.dto;

import com.example.backend.model.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;


/**
 * ユーザー取得レスポンス用DTO
 */
@Getter
@Builder
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private String fastname;
    private String familyname;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .fastname(user.getFastname())
                .familyname(user.getFamilyname())
                .createdAt(user.getCreatedAt())
                .createdBy(user.getCreatedBy())
                .updatedAt(user.getUpdatedAt())
                .updatedBy(user.getUpdatedBy())
                .build();
    }
}
