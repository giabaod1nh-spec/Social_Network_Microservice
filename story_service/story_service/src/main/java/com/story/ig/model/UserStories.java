package com.story.ig.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Document(collection = "UserStories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class UserStories {
    @Id
    @Field("user_id")
    private String userId;

    @Field("list_stories")
    private List<Story> listStories;
}
