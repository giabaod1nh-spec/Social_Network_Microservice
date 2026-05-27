package com.identity_service.identity.dto.request;

import jakarta.validation.constraints.Size;
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
    @Size(min = 4 , message = "USERNAME_INVALID")
    String userName;

    String avatar;
    @Size(min = 6 , message = "INVALID_PASSWORD")
    String password;

    String email;

    String firstName;

    String lastName;

    String gender;

    LocalDate dob;

    String address;

    String phone;
}
