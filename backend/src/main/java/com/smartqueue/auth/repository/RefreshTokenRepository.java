package com.smartqueue.auth.repository;

import com.smartqueue.auth.entity.RefreshToken;
import com.smartqueue.user.entity.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  List<RefreshToken> findAllByFamilyId(UUID familyId);

  List<RefreshToken> findAllByUserAndRevokedAtIsNull(UserAccount user);
}
