package com.example.usermanagementwithredis.dtos;

import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UserRequest {
    @NotNull(message = "First name cannot be empty")
    private String firstName;
    @NotNull(message = "Last name cannot be empty")
    private String lastName;
    @NotNull(message = "Email cannot be empty")
    private String email;
    @NotNull(message = "Password cannot be empty")
    private String password;
    private Set<RoleRequest> roles;
}
