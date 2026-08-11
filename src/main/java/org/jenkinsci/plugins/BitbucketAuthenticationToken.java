package org.jenkinsci.plugins;

import java.util.Collection;
import java.util.List;

import org.jenkinsci.plugins.api.BitbucketApiService;
import org.jenkinsci.plugins.api.BitbucketUser;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.scribe.model.Token;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class BitbucketAuthenticationToken extends AbstractAuthenticationToken {

    private static final long serialVersionUID = -7826610577724673531L;

    private Token accessToken;
    private BitbucketUser bitbucketUser;

    public BitbucketAuthenticationToken(Token accessToken, String apiKey, String apiSecret) {
        super(null);
        this.accessToken = accessToken;
        this.bitbucketUser = new BitbucketApiService(apiKey, apiSecret).getUserByToken(accessToken);

        boolean authenticated = false;

        if (bitbucketUser != null) {
            authenticated = true;
        }

        setAuthenticated(authenticated);
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return this.bitbucketUser != null ? this.bitbucketUser.getAuthorities() : List.of();
    }

    /**
     * @return the accessToken
     */
    public Token getAccessToken() {
        return accessToken;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return getName();
    }

    @Override
    public String getName() {
        return (bitbucketUser != null ? bitbucketUser.getUsername() : null);
    }

    @Restricted(NoExternalUse.class)
    public BitbucketUser getBitbucketUser(){
        return bitbucketUser;
    }
}
