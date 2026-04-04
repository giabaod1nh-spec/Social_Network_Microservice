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

    private String comment;

    @Column(name = "create_at")
    private LocalDateTime commentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private Post post;
}
