package com.identity_service.identity.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UserCreationRequest {
    String userName;

    String avatar;

    String password;

    String email;

    String firstName;

    String lastName;

    String gender;

    LocalDate dob;

    String address;

    String phone;
}
