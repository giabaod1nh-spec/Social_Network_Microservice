package com.ig.PostService.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "post")
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Post {
    @Id
    private String id;
    @Column(name = "user_id")
    private String userId;
    @Column(name = "first_name")
    private String firstName;
    @Column(name ="avatar_url")
    private String avatarUrl;
    @Column(name = "last_name")
    private String lastName;
    @Column(name = "user_name")
    private String userName;
    private String description;
    private Long liked;
    @Column(name = "create_at")
    private LocalDateTime createAt;
    @Column(name = "url_media")
    private String urlMedia;
    @OneToMany(mappedBy = "post")
    private List<Comment> commentList;
}
