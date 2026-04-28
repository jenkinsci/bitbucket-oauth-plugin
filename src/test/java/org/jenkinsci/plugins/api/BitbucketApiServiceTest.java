package org.jenkinsci.plugins.api;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scribe.model.Token;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

public class BitbucketApiServiceTest {

    private WireMockServer wireMock;
    private BitbucketApiService service;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        wireMock.stubFor(get(urlPathEqualTo("/2.0/user"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"username\":\"testuser\",\"display_name\":\"Test User\"}")));

        wireMock.stubFor(get(urlPathEqualTo("/2.0/user/workspaces"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"values\":[]}")));

        service = new BitbucketApiService("clientId", "clientSecret");
        service.setApiEndpoint("http://localhost:" + wireMock.port() + "/2.0/");
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void userRequest_usesBearerAuthorizationHeader() {
        service.getUserByToken(new Token("test-access-token", ""));

        List<LoggedRequest> requests = wireMock.findAll(getRequestedFor(urlPathEqualTo("/2.0/user")));
        assertEquals(1, requests.size());
        assertEquals("Bearer test-access-token", requests.get(0).getHeader("Authorization"));
    }

    @Test
    void userRequest_doesNotPassTokenAsQueryParameter() {
        service.getUserByToken(new Token("test-access-token", ""));

        List<LoggedRequest> requests = wireMock.findAll(getRequestedFor(urlPathEqualTo("/2.0/user")));
        assertEquals(1, requests.size());
        assertFalse(requests.get(0).queryParameter("access_token").isPresent());
    }

    @Test
    void workspacesRequest_usesBearerAuthorizationHeader() {
        service.getUserByToken(new Token("test-access-token", ""));

        List<LoggedRequest> requests = wireMock.findAll(getRequestedFor(urlPathEqualTo("/2.0/user/workspaces")));
        assertEquals(1, requests.size());
        assertEquals("Bearer test-access-token", requests.get(0).getHeader("Authorization"));
    }

    @Test
    void workspacesRequest_doesNotPassTokenAsQueryParameter() {
        service.getUserByToken(new Token("test-access-token", ""));

        List<LoggedRequest> requests = wireMock.findAll(getRequestedFor(urlPathEqualTo("/2.0/user/workspaces")));
        assertEquals(1, requests.size());
        assertFalse(requests.get(0).queryParameter("access_token").isPresent());
    }
}
