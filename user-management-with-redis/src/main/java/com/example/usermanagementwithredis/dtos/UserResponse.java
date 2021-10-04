package com.example.usermanagementwithredis.dtos;

import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UserResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private Set<RoleResponse> roles;
}
