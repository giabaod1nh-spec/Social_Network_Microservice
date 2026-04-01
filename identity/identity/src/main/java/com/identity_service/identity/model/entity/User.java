package com.identity_service.identity.model.entity;

import com.common_library.common.model.BaseAuditEntity;
import com.identity_service.identity.model.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "users")
public class User extends BaseAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String userId;

    @Column(name = "user_name" , unique = true)
    String userName;

    @Column(name = "password" , nullable = false)
    String password;

    @Column(name = "email" , unique = true , nullable = false)
    String email;

    @Column(name = "email_verified" , nullable = false , columnDefinition = "boolean default false")
    Boolean emailVerified;

    @Column(name = "user_status")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    UserStatus userStatus;

    @OneToMany(mappedBy = "users" , cascade = CascadeType.ALL , orphanRemoval = true)
    List<RefreshToken> tokens;

    @OneToMany(mappedBy = "users" , cascade = CascadeType.ALL , orphanRemoval = true)
    List<EmailVerifyToken> emailVerifyTokens;
}
