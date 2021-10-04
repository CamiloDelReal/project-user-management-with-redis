package com.example.usermanagementwithredis.dtos;

import lombok.*;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LoginRequest {
    @NotNull(message = "Email cannot be empty")
    private String email;
    @NotNull(message = "Password cannot be empty")
    private String password;
}
