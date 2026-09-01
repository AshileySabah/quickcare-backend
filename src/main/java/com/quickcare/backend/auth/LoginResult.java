package com.quickcare.backend.auth;

import com.quickcare.backend.auth.dto.LoginResponse;

record LoginResult(
        LoginResponse response,
        String accessToken,
        String refreshToken
) {
}
