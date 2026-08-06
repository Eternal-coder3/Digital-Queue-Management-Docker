package com.smartqueue.auth.service;

import com.smartqueue.auth.config.JwtProperties;
import com.smartqueue.auth.dto.request.ForgotPasswordRequest;
import com.smartqueue.auth.dto.request.LoginRequest;
import com.smartqueue.auth.dto.request.RegisterRequest;
import com.smartqueue.auth.dto.request.ResetPasswordRequest;
import com.smartqueue.auth.dto.response.AuthResponse;
import com.smartqueue.auth.dto.response.AuthSession;
import com.smartqueue.auth.entity.PasswordResetToken;
import com.smartqueue.auth.entity.RefreshToken;
import com.smartqueue.auth.exception.AccountDisabledException;
import com.smartqueue.auth.exception.DuplicateEmailException;
import com.smartqueue.auth.exception.InvalidCredentialsException;
import com.smartqueue.auth.repository.PasswordResetTokenRepository;
import com.smartqueue.auth.repository.RefreshTokenRepository;
import com.smartqueue.notification.service.NotificationClient;
import com.smartqueue.user.entity.Role;
import com.smartqueue.user.entity.UserAccount;
import com.smartqueue.user.enums.RoleName;
import com.smartqueue.user.repository.RoleRepository;
import com.smartqueue.user.repository.UserAccountRepository;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserAccountRepository userAccountRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final NotificationClient notificationClient;
  private final PasswordResetTokenRepository passwordResetTokens;
  private final RefreshTokenRepository refreshTokens;
  private final JwtProperties jwtProperties;
  private final String passwordResetUrl;
  private final SecureRandom secureRandom = new SecureRandom();

  public AuthService(
      UserAccountRepository userAccountRepository,
      RoleRepository roleRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      NotificationClient notificationClient,
      PasswordResetTokenRepository passwordResetTokens,
      RefreshTokenRepository refreshTokens,
      JwtProperties jwtProperties,
      @Value("${smartqueue.auth.password-reset-url:http://localhost:8080/reset-password}")
          String passwordResetUrl) {
    this.userAccountRepository = userAccountRepository;
    this.roleRepository = roleRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.notificationClient = notificationClient;
    this.passwordResetTokens = passwordResetTokens;
    this.refreshTokens = refreshTokens;
    this.jwtProperties = jwtProperties;
    this.passwordResetUrl = passwordResetUrl;
  }

  @Transactional
  public AuthSession register(RegisterRequest request) {
    String email = normalizeEmail(request.email());
    if (userAccountRepository.existsByEmail(email)) {
      throw new DuplicateEmailException();
    }

    Role citizenRole =
        roleRepository
            .findByName(RoleName.CITIZEN)
            .orElseThrow(() -> new IllegalStateException("CITIZEN role is not configured"));
    UserAccount user =
        new UserAccount(
            UUID.randomUUID(), email, passwordEncoder.encode(request.password()), citizenRole);
    UserAccount savedUser = userAccountRepository.save(user);

    // Send Welcome Email via .NET Notification Service
    notificationClient.sendWelcomeEmail(savedUser.getEmail());

    return createSession(savedUser, UUID.randomUUID());
  }

  @Transactional
  public AuthSession login(LoginRequest request) {
    UserAccount user =
        userAccountRepository
            .findByEmail(normalizeEmail(request.email()))
            .orElseThrow(InvalidCredentialsException::new);

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    if (!user.isEnabled()) {
      throw new AccountDisabledException();
    }
    if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
      user.setPasswordHash(passwordEncoder.encode(request.password()));
    }

    return createSession(user, UUID.randomUUID());
  }

  @Transactional
  public AuthSession refresh(String rawToken) {
    RefreshToken current =
        refreshTokens.findByTokenHash(hashToken(rawToken)).orElseThrow(() -> invalidRefreshToken());
    if (current.getRevokedAt() != null) {
      revokeFamily(current.getFamilyId());
      throw invalidRefreshToken();
    }
    if (!current.getExpiresAt().isAfter(Instant.now()) || !current.getUser().isEnabled()) {
      current.revoke();
      throw invalidRefreshToken();
    }
    current.revoke();
    return createSession(current.getUser(), current.getFamilyId());
  }

  @Transactional
  public void logout(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return;
    }
    refreshTokens.findByTokenHash(hashToken(rawToken)).ifPresent(RefreshToken::revoke);
  }

  /** Always succeeds publicly so callers cannot discover which emails have accounts. */
  @Transactional
  public void requestPasswordReset(ForgotPasswordRequest request) {
    userAccountRepository
        .findByEmail(normalizeEmail(request.email()))
        .ifPresent(
            user -> {
              passwordResetTokens.deleteByUserAndUsedAtIsNull(user);
              String rawToken = newResetToken();
              passwordResetTokens.save(
                  new PasswordResetToken(
                      user, hashToken(rawToken), Instant.now().plus(Duration.ofMinutes(30))));
              notificationClient.sendPasswordResetEmail(
                  user.getEmail(), passwordResetUrl + "?token=" + rawToken);
            });
  }

  @Transactional
  public void resetPassword(ResetPasswordRequest request) {
    PasswordResetToken resetToken =
        passwordResetTokens
            .findByTokenHash(hashToken(request.token()))
            .orElseThrow(
                () ->
                    new com.smartqueue.common.exception.BusinessConflictException(
                        "This password reset link is invalid or has expired"));
    if (resetToken.getUsedAt() != null || !resetToken.getExpiresAt().isAfter(Instant.now())) {
      throw new com.smartqueue.common.exception.BusinessConflictException(
          "This password reset link is invalid or has expired");
    }
    resetToken.getUser().setPasswordHash(passwordEncoder.encode(request.password()));
    resetToken.markUsed();
    passwordResetTokens.deleteByUserAndUsedAtIsNull(resetToken.getUser());
    refreshTokens
        .findAllByUserAndRevokedAtIsNull(resetToken.getUser())
        .forEach(RefreshToken::revoke);
  }

  private String newResetToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private AuthSession createSession(UserAccount user, UUID familyId) {
    byte[] bytes = new byte[48];
    secureRandom.nextBytes(bytes);
    String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant refreshExpiresAt = Instant.now().plus(jwtProperties.refreshExpiration());
    refreshTokens.save(
        new RefreshToken(user, hashToken(rawRefreshToken), familyId, refreshExpiresAt));
    return new AuthSession(createAuthResponse(user), rawRefreshToken, refreshExpiresAt);
  }

  private void revokeFamily(UUID familyId) {
    refreshTokens.findAllByFamilyId(familyId).forEach(RefreshToken::revoke);
  }

  private com.smartqueue.common.exception.BusinessConflictException invalidRefreshToken() {
    return new com.smartqueue.common.exception.BusinessConflictException(
        "Refresh token is invalid or expired");
  }

  public boolean refreshCookieSecure() {
    return jwtProperties.refreshCookieSecure();
  }

  private String hashToken(String token) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to protect password reset token", exception);
    }
  }

  private AuthResponse createAuthResponse(UserAccount user) {
    return new AuthResponse(
        jwtService.createAccessToken(user),
        "Bearer",
        jwtService.expiresAt(),
        user.getPublicId(),
        user.getEmail(),
        user.getRole().getName().name());
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
