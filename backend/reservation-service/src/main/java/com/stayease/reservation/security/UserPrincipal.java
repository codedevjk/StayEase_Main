package com.stayease.reservation.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPrincipal implements UserDetails {

    private Long id;
    private String email;
    private String role;
    private Long tokenVersion;
    private Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(Long id, String email, String role, Long tokenVersion) {
        String authorityRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(authorityRole));
        return UserPrincipal.builder()
                .id(id)
                .email(email)
                .role(authorityRole)
                .tokenVersion(tokenVersion)
                .authorities(authorities)
                .build();
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
