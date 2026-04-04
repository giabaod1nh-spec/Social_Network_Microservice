package com.ig.PostService.payload.response;

import com.ig.PostService.model.Post;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserPostProfileResponse {
    private String userId;
    private List<PostResponse> listUserPost = new ArrayList<>();
}
