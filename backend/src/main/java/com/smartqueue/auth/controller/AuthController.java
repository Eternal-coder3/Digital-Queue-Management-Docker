package com.smartqueue.auth.controller;

import com.smartqueue.auth.dto.request.ForgotPasswordRequest;
import com.smartqueue.auth.dto.request.LoginRequest;
import com.smartqueue.auth.dto.request.RegisterRequest;
import com.smartqueue.auth.dto.request.ResetPasswordRequest;
import com.smartqueue.auth.dto.response.AuthResponse;
import com.smartqueue.auth.dto.response.AuthSession;
import com.smartqueue.auth.service.AuthService;
import com.smartqueue.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private static final String REFRESH_COOKIE = "smartqueue_refresh";

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthResponse>> register(
      @Valid @RequestBody RegisterRequest request) {
    return sessionResponse(authService.register(request), HttpStatus.CREATED);
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
    return sessionResponse(authService.login(request), HttpStatus.OK);
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<AuthResponse>> refresh(
      @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new com.smartqueue.common.exception.BusinessConflictException(
          "Refresh token is missing");
    }
    return sessionResponse(authService.refresh(refreshToken), HttpStatus.OK);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken) {
    authService.logout(refreshToken);
    ResponseCookie expired =
        ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true)
            .secure(authService.refreshCookieSecure())
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(0)
            .build();
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expired.toString()).build();
  }

  @PostMapping("/forgot-password")
  public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    authService.requestPasswordReset(request);
    return ApiResponse.success(null);
  }

  @PostMapping("/reset-password")
  public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request);
    return ApiResponse.success(null);
  }

  private ResponseEntity<ApiResponse<AuthResponse>> sessionResponse(
      AuthSession session, HttpStatus status) {
    ResponseCookie cookie =
        ResponseCookie.from(REFRESH_COOKIE, session.refreshToken())
            .httpOnly(true)
            .secure(authService.refreshCookieSecure())
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(java.time.Duration.between(java.time.Instant.now(), session.refreshExpiresAt()))
            .build();
    return ResponseEntity.status(status)
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(ApiResponse.success(session.response()));
  }
}
