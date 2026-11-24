package com.ttladvisor.user.dto.response;

import com.ttladvisor.user.domain.entity.User;
import java.time.Instant;

public record UserResponse(Long id, String name, String email, String company, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCompany(),
                user.getCreatedAt());
    }
}
