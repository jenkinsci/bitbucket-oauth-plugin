package org.jenkinsci.plugins.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.apache.commons.lang.StringUtils;
import java.util.Collection;

public class BitbucketUser implements UserDetails {

    public String username = StringUtils.EMPTY;

    List<GrantedAuthority> grantedAuthorties = new ArrayList<GrantedAuthority>();

    public BitbucketUser() {
        super();
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return grantedAuthorties;
    }

    public void addAuthority(String role)
    {
        grantedAuthorties.add(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return this.username;
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
