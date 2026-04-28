package org.jenkinsci.plugins.api;

import org.scribe.builder.api.DefaultApi20;
import org.scribe.extractors.AccessTokenExtractor;
import org.scribe.extractors.JsonTokenExtractor;
import org.scribe.model.OAuthConfig;
import org.scribe.model.OAuthConstants;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuth20ServiceImpl;
import org.scribe.oauth.OAuthService;

public class BitbucketApiV2 extends DefaultApi20 {
    private static final String OAUTH_ENDPOINT = "https://bitbucket.org/site/oauth2/";

    @Override
    public String getAccessTokenEndpoint() {
        return OAUTH_ENDPOINT + "access_token";
    }

    @Override
    public String getAuthorizationUrl(OAuthConfig config) {
        return OAUTH_ENDPOINT + "authorize" + "?client_id=" + config.getApiKey() + "&response_type=code";
    }

    @Override
    public Verb getAccessTokenVerb() {
        return Verb.POST;
    }

    @Override
    public OAuthService createService(OAuthConfig config) {
        return new BitbucketOAuth20Service(this, config);
    }

    @Override
    public AccessTokenExtractor getAccessTokenExtractor() {
        return new JsonTokenExtractor();
    }

    private static class BitbucketOAuth20Service extends OAuth20ServiceImpl {

        private static final String GRANT_TYPE = "grant_type";

        private DefaultApi20 api;
        private OAuthConfig config;

        public BitbucketOAuth20Service(DefaultApi20 api, OAuthConfig config) {
            super(api, config);
            this.api = api;
            this.config = config;
        }

        public Token getAccessToken(Token requestToken, Verifier verifier) {
            OAuthRequest request = new OAuthRequest(api.getAccessTokenVerb(), api.getAccessTokenEndpoint());
            request.addBodyParameter(OAuthConstants.CLIENT_ID, config.getApiKey());
            request.addBodyParameter(OAuthConstants.CLIENT_SECRET, config.getApiSecret());
            request.addBodyParameter(OAuthConstants.CODE, verifier.getValue());
            request.addBodyParameter(GRANT_TYPE, "authorization_code");

            Response response = request.send();
            return api.getAccessTokenExtractor().extract(response.getBody());
        }

        @Override
        public void signRequest(Token accessToken, OAuthRequest request) {
            request.addHeader("Authorization", "Bearer " + accessToken.getToken());
        }

    }

}