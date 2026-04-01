package com.identity_service.identity.repository;

import com.identity_service.identity.model.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken , String> {
    Optional<RefreshToken> findByRefreshToken(String refreshToken);

    void deleteRefreshTokenByRefreshToken(String refreshToken);
}
