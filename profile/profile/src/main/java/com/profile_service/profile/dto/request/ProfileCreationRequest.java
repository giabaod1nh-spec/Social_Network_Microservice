package com.profile_service.profile.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfileCreationRequest {
    String userId;

    String avatar;

    String firstName;

    String lastName;

    String gender;

    LocalDate dob;

    String address;

    String phone;

}
