package org.jenkinsci.plugins;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpSession;

import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.api.BitbucketApiService;
import org.jenkinsci.plugins.api.BitbucketGroup;
import org.jenkinsci.plugins.api.BitbucketUser;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Header;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.scribe.model.Token;
import org.springframework.dao.DataAccessException;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.User;
import hudson.security.GroupDetails;
import hudson.security.SecurityRealm;
import hudson.security.UserMayOrMayNotExistException;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.security.SecurityListener;

public class BitbucketSecurityRealm extends SecurityRealm {

    private static final String STATE_ATTRIBUTE = BitbucketSecurityRealm.class.getName() + ".state";
    private static final String REFERER_ATTRIBUTE = BitbucketSecurityRealm.class.getName() + ".referer";
    private static final Logger LOGGER = Logger.getLogger(BitbucketSecurityRealm.class.getName());

    private String clientID;
    @Deprecated
    private String clientSecret;
    private Secret secretClientSecret;

    @DataBoundConstructor
    public BitbucketSecurityRealm(String clientID, String clientSecret, Secret secretClientSecret) {
        super();
        this.clientID = Util.fixEmptyAndTrim(clientID);
        this.clientSecret = Util.fixEmptyAndTrim(clientSecret);
        this.secretClientSecret = secretClientSecret;
    }

    public BitbucketSecurityRealm() {
        super();
        LOGGER.log(Level.FINE, "BitbucketSecurityRealm()");
    }

    /**
     * @return the clientID
     */
    public String getClientID() {
        return clientID;
    }

    /**
     * @param clientID the clientID to set
     */
    public void setClientID(String clientID) {
        this.clientID = clientID;
    }

    /**
     * @return the clientSecret
     */
    @Deprecated
    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * @param clientSecret the clientSecret to set
     */
    @Deprecated
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    /**
     * @return the secretClientSecret
     */
    public Secret getSecretClientSecret() {
        // for backward compatibility
        if (StringUtils.isNotEmpty(clientSecret)) {
            return Secret.fromString(clientSecret);
        }
        return secretClientSecret;
    }

    /**
     * @param secretClientSecret the secretClientSecret to set
     */
    public void setSecretClientSecret(Secret secretClientSecret) {
        this.secretClientSecret = secretClientSecret;
    }

    public HttpResponse doCommenceLogin(StaplerRequest request, @Header("Referer") final String referer)
            throws IOException {

        String state = RandomStringUtils.random(64, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~");
        request.getSession().setAttribute(STATE_ATTRIBUTE, state);
        request.getSession().setAttribute(REFERER_ATTRIBUTE, referer);

        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            throw new RuntimeException("Jenkins is not started yet.");
        }
        String rootUrl = jenkins.getRootUrl();
        if (StringUtils.endsWith(rootUrl, "/")) {
            rootUrl = StringUtils.left(rootUrl, StringUtils.length(rootUrl) - 1);
        }
        String callback = rootUrl + "/securityRealm/finishLogin";

        BitbucketApiService bitbucketApiService = new BitbucketApiService(clientID, getSecretClientSecret().getPlainText(), callback);

        return new HttpRedirect(bitbucketApiService.createAuthorizationCodeURL(null, state));
    }

    public HttpResponse doFinishLogin(StaplerRequest request) throws IOException {
        String code = request.getParameter("code");
        String state = request.getParameter("state");

        if (StringUtils.isBlank(code)) {
            LOGGER.log(Level.SEVERE, "doFinishLogin() code = null");
            return HttpResponses.redirectToContextRoot();
        }

        if (state == null || !StringUtils.equals(state, getSessionAttribute(request, STATE_ATTRIBUTE))) {
            LOGGER.log(Level.SEVERE, "doFinishLogin() invalid state parameter");
            return HttpResponses.redirectToContextRoot();
        }

        String referer = getSessionAttribute(request, REFERER_ATTRIBUTE);

        // avoid session fixation vulnerability
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        request.getSession(true);

        String rawClientSecret = getSecretClientSecret().getPlainText();

        Token accessToken = new BitbucketApiService(clientID, rawClientSecret).getTokenByAuthorizationCode(code, null);

        if (!accessToken.isEmpty()) {

            BitbucketAuthenticationToken auth = new BitbucketAuthenticationToken(accessToken, clientID, rawClientSecret);
            SecurityContextHolder.getContext().setAuthentication(auth);

            User u = User.current();
            if (u != null) {
                u.setFullName(auth.getName());
            }

            BitbucketUser userDetails = auth.getBitbucketUser();
            if (userDetails != null) {
                SecurityListener.fireAuthenticated(userDetails);
            }
        } else {
            LOGGER.log(Level.SEVERE, "doFinishLogin() accessToken = null");
        }

        // redirect to referer
        if (referer != null) {
            return HttpResponses.redirectTo(referer);
        } else {
            return HttpResponses.redirectToContextRoot();
        }
    }

    @Override
    public SecurityComponents createSecurityComponents() {
        return new SecurityRealm.SecurityComponents(new AuthenticationManager() {
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                if (authentication instanceof BitbucketAuthenticationToken) {
                    return authentication;
                }

                throw new BadCredentialsException("Unexpected authentication type: " + authentication);
            }
        }, new UserDetailsService() {
            public UserDetails loadUserByUsername(String username)
                    throws UserMayOrMayNotExistException, DataAccessException {
                throw new UserMayOrMayNotExistException("Cannot verify users in this context");
            }
        });
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserDetails result = null;
        Authentication token = SecurityContextHolder.getContext().getAuthentication();
        if (token == null) {
            throw new UsernameNotFoundException("BitbucketAuthenticationToken = null, no known user: " + username);
        }
        if (!(token instanceof BitbucketAuthenticationToken)) {
            throw new UserMayOrMayNotExistException("Unexpected authentication type: " + token);
        }
        result = new BitbucketApiService(clientID, getSecretClientSecret().getPlainText()).getUserByUsername(username);
        if (result == null) {
            throw new UsernameNotFoundException("User does not exist for login: " + username);
        }
        return result;
    }

    @Override
    public GroupDetails loadGroupByGroupname(String groupName) {
        if (groupName.contains("::")) {
            return new BitbucketGroup(groupName);
        } else {
            throw new UsernameNotFoundException("Group does not exist:" + groupName);
        }
    }

    @Override
    public boolean allowsSignup() {
        return false;
    }

    @Override
    public String getLoginUrl() {
        return "securityRealm/commenceLogin";
    }

    private String getSessionAttribute(StaplerRequest request, String attributeName) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return (String) session.getAttribute(attributeName);
    }

    public static final class ConverterImpl implements Converter {

        public boolean canConvert(Class type) {
            return type == BitbucketSecurityRealm.class;
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {

            BitbucketSecurityRealm realm = (BitbucketSecurityRealm) source;

            writer.startNode("clientID");
            writer.setValue(realm.getClientID());
            writer.endNode();

            writer.startNode("secretClientSecret");
            writer.setValue(realm.getSecretClientSecret().getEncryptedValue());
            writer.endNode();
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {

            String node = reader.getNodeName();

            reader.moveDown();

            BitbucketSecurityRealm realm = new BitbucketSecurityRealm();

            node = reader.getNodeName();

            String value = reader.getValue();

            setValue(realm, node, value);

            reader.moveUp();

            reader.moveDown();

            node = reader.getNodeName();

            value = reader.getValue();

            setValue(realm, node, value);

            reader.moveUp();

            if (reader.hasMoreChildren()) {
                reader.moveDown();

                node = reader.getNodeName();

                value = reader.getValue();

                setValue(realm, node, value);

                reader.moveUp();
            }
            return realm;
        }

        private void setValue(BitbucketSecurityRealm realm, String node, String value) {

            if (node.equalsIgnoreCase("clientid")) {
                realm.setClientID(value);
            } else if (node.equalsIgnoreCase("clientsecret")) {
                realm.setClientSecret(value);
            } else if (node.equalsIgnoreCase("secretclientsecret")) {
                realm.setSecretClientSecret(Secret.fromString(value));
            } else {
                throw new ConversionException("invalid node value = " + node);
            }

        }
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<SecurityRealm> {

        @Override
        public String getHelpFile() {
            return "/plugin/bitbucket-oauth/help/help-security-realm.html";
        }

        @Override
        public String getDisplayName() {
            return "Bitbucket OAuth Plugin";
        }

        public DescriptorImpl() {
            super();
        }

        public DescriptorImpl(Class<? extends SecurityRealm> clazz) {
            super(clazz);
        }
    }

}
