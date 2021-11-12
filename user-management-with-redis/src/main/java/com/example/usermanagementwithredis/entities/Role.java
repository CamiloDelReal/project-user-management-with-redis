package com.example.usermanagementwithredis.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@RedisHash("roles")
public class Role {
    public static final String ADMINISTRATOR = "Administrator";
    public static final String GUEST = "Guest";

    @Id
    @Indexed
    private Long id;
    @Indexed
    private String name;

    public Role(String name) {
        this.name = name;
    }
}
