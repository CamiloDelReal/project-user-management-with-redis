package com.example.usermanagementwithredis.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class LoginResponse {
    private String email;
    private String tokenType;
    private String token;
}
