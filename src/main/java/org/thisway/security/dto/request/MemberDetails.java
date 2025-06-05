package org.thisway.security.dto.request;

import java.util.Collection;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.Assert;
import org.thisway.member.entity.MemberRole;

@Getter
@Value
public class MemberDetails implements UserDetails {

    String username;
    long companyId;
    String password;
    MemberRole role;

    @Builder
    public MemberDetails(String username, long companyId, String password, MemberRole role) {
        this.username = username;
        this.companyId = companyId;
        this.password = password;
        this.role = role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Assert.isTrue(
                !role.name().startsWith("ROLE_"),
                () -> role + " cannot start with ROLE_ (it is automatically added)"
        );
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}
