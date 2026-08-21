package com.pawwalk.server.global.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.UUID;

@Getter
public class PrincipalDetails extends User {
    private final UUID userId; // 실제 DB(users.id)의 PK 값

    public PrincipalDetails(UUID userId, String username, String password, Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.userId = userId;
    }
}
