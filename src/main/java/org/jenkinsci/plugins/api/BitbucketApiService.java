package org.jenkinsci.plugins.api;

import java.util.logging.Logger;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.scribe.builder.ServiceBuilder;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

import com.google.gson.Gson;

public class BitbucketApiService {

    private static final Logger LOGGER = Logger.getLogger(BitbucketApiService.class.getName());

    private static final String API2_ENDPOINT = "https://api.bitbucket.org/2.0/";

    private OAuthService service;

    public BitbucketApiService(String apiKey, String apiSecret) {
        this(apiKey, apiSecret, null);
    }

    public BitbucketApiService(String apiKey, String apiSecret, String callback) {
        super();
        ServiceBuilder builder = new ServiceBuilder().provider(BitbucketApiV2.class).apiKey(apiKey).apiSecret(apiSecret);
        if (StringUtils.isNotBlank(callback)) {
            builder.callback(callback);
        }
        service = builder.build();
    }

public String createAuthorizationCodeURL(Token requestToken, String state) {
        return service.getAuthorizationUrl(requestToken) + "&state=" + state;
    }

    public Token getTokenByAuthorizationCode(String code, Token requestToken) {
        Verifier v = new Verifier(code);
        return service.getAccessToken(requestToken, v);
    }

    public BitbucketUser getUserByToken(Token accessToken) {
        BitbucketUser bitbucketUser = getBitbucketUser(accessToken);

        bitbucketUser.addAuthority("authenticated");

        findAndAddUserWorkspaceAccess(accessToken, bitbucketUser);

        return bitbucketUser;
    }

    private BitbucketUser getBitbucketUser(Token accessToken) {
        BitbucketUser bitbucketUser = getBitbucketUserV2(accessToken);
        if (bitbucketUser != null) {
            return bitbucketUser;
        }
        throw new BitbucketMissingPermissionException(
                "Your Bitbucket credentials lack one required privilege scopes: [Account Read]");
    }

    private BitbucketUser getBitbucketUserV2(Token accessToken) {
        // require "Account Read" permission
        OAuthRequest request = new OAuthRequest(Verb.GET, API2_ENDPOINT + "user");
        service.signRequest(accessToken, request);
        Response response = request.send();
        String json = response.getBody();
        Gson gson = new Gson();
        BitbucketUser bitbucketUser = gson.fromJson(json, BitbucketUser.class);
        if (bitbucketUser == null || StringUtils.isEmpty(bitbucketUser.username)) {
            return null;
        }
        return bitbucketUser;
    }

    private void findAndAddUserWorkspaceAccess(Token accessToken, BitbucketUser bitbucketUser) {
        Gson gson = new Gson();
        String url = API2_ENDPOINT + "user/workspaces";
        try {
            do {
                OAuthRequest request1 = new OAuthRequest(Verb.GET, url);
                service.signRequest(accessToken, request1);
                Response response1 = request1.send();
                String json1 = response1.getBody();

                LOGGER.finest("Response from bitbucket api " + url);
                LOGGER.finest(json1);

                BitbucketWorkspaceAccessibleResponse bitBucketTeamsResponse = gson.fromJson(json1, BitbucketWorkspaceAccessibleResponse.class);

                if (CollectionUtils.isNotEmpty(bitBucketTeamsResponse.getTeamsList())) {
                    for (BitbucketWorkspaceAccessible team : bitBucketTeamsResponse.getTeamsList()) {
                        String workspaceSlug = team.getWorkspace().getSlug();

                        // Always grant member and collaborator
                        bitbucketUser.addAuthority(workspaceSlug + "::member");
                        bitbucketUser.addAuthority(workspaceSlug + "::collaborator"); // for backward compatibility

                        // Grant owner and administrator if the user is an administrator of the workspace
                        if (team.isAdministrator()) {
                            bitbucketUser.addAuthority(workspaceSlug + "::administrator");
                            bitbucketUser.addAuthority(workspaceSlug + "::owner"); // for backward compatibility
                        }
                    }
                }
                url = bitBucketTeamsResponse.getNext();
            } while (url != null);
        } catch (Exception e) {
            // Some error, So ignore it and move on.
            e.printStackTrace();
        }
    }

}
