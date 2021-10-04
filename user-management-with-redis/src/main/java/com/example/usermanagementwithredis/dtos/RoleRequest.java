package com.example.usermanagementwithredis.dtos;

import lombok.*;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RoleRequest {
    @NotNull(message = "Role id cannot be empty")
    private Long id;
}
