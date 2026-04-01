package com.chat.chat_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class UserProfileResponse {
    String userId;

    String userName;

    String firstName;

    String lastName;

    String gender;

    LocalDate dob;

    String address;

    String phone;

}
