package com.smartqueue.auth.dto.response;

import java.time.Instant;

public record AuthSession(AuthResponse response, String refreshToken, Instant refreshExpiresAt) {}
