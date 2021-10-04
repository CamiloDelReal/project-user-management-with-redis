package com.example.usermanagementwithredis.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Reference;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@RedisHash("users")
public class User {
    @Id
    @Indexed
    private Long id;
    private String firstName;
    private String lastName;
    @Indexed
    private String email;
    private String protectedPassword;
    @Reference
    private Set<Role> roles;

    public User(String firstName, String lastName, String email, String protectedPassword, Set<Role> roles) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.protectedPassword = protectedPassword;
        this.roles = roles;
    }
}
