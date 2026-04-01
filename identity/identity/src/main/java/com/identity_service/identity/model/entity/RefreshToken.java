package com.identity_service.identity.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Table(name = "refresh_token")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long tokenId;

    @Column(nullable = false , unique = true , length = 1000)
    String refreshToken;

        @ManyToOne(fetch = FetchType.EAGER)
        @JoinColumn(name = "userId" , nullable = false)
        User users;
}
