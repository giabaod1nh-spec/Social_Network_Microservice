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

    private String description;

    private Long liked;

    @Column(name = "create_at")
    private LocalDateTime createAt;

    @Column(name = "url_media")
    private String urlMedia;

    @OneToMany(mappedBy = "post" , cascade =  CascadeType.ALL, orphanRemoval = true)
    private List<Comment> commentList;
}
