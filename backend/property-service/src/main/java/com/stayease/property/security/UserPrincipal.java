package com.stayease.property.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Principal representing an authenticated user within Spring Security in Property Service.
 */
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String role;
    private final Long tokenVersion;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long id, String email, String role, Long tokenVersion,
                         Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.tokenVersion = tokenVersion;
        this.authorities = authorities;
    }

    public static UserPrincipal create(Long userId, String email, String role, Long tokenVersion) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
        return new UserPrincipal(userId, email, role, tokenVersion, authorities);
    }

    public Long getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public Long getTokenVersion() {
        return tokenVersion;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
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
