package com.identity_service.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthRequest {
    @NotBlank(message = "MUST_NOT_BLANK")
    String userName;
    @NotBlank(message = "MUST_NOT_BLANK")
    String password;
}
