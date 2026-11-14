package com.quickcare.backend.auth;

import com.quickcare.backend.auth.dto.LoginResponse;

public record LoginResult(
        LoginResponse response,
        String token
) {
}
