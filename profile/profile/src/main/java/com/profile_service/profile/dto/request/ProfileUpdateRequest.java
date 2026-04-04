package com.profile_service.profile.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class ProfileUpdateRequest {
    String firstName;
    String lastName;
    LocalDate dob;
    String address;
}
