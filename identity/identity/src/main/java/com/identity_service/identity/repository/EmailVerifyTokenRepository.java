package com.identity_service.identity.repository;

import com.identity_service.identity.model.entity.EmailVerifyToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerifyTokenRepository extends JpaRepository<com.identity_service.identity.model.entity.EmailVerifyToken , String> {
    Optional<EmailVerifyToken> findByEmailVerifyToken(String emailVerifyToken);
}
