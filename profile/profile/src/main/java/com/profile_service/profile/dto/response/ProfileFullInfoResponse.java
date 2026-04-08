package com.profile_service.profile.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfileFullInfoResponse {
    String userId;

    String userName;

    String avatar;

    String firstName;

    String lastName;

    String gender;

    LocalDate dob;

    String address;

    String phone;

    Long follower;

    Long followed;
}
