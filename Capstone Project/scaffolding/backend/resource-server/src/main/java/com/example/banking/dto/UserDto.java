package com.example.banking.dto;

import com.example.banking.model.BankUserEntity;

public record UserDto(
        String userId,
        String email,
        String displayName,
        String role
) {
    public static UserDto from(BankUserEntity user) {
        return new UserDto(
                user.getUserId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name()
        );
    }
}
