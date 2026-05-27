package com.ig.PostService.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "comment")
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Column(name = "user_name")
    private String userName;
    @Column(name = "first_name")
    private String firstName;
    @Column(name = "last_name")
    private String lastName;
    @Column(name = "avatar_url")
    private String avatarUrl;
    private String comment;
    @Column(name = "create_at")
    private LocalDateTime commentAt;
    @Column(name = "liked")
    private Long liked;
    @ManyToOne
    private Post post;
}
