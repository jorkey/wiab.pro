package org.waveprotocol.box.server.rpc;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeResponseUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.servlet.auth.oauth2.AbstractAuthorizationCodeCallbackServlet;
import com.google.api.client.http.GenericUrl;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.waveprotocol.box.server.authentication.GoogleAuthentication;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.robots.agent.welcome.WelcomeRobot;
import org.waveprotocol.box.server.util.RegistrationUtil;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * @author akaplanov@gamil.com (Andrew Kaplanov)
 */
public class GoogleAuthenticationCallbackServlet extends AbstractAuthorizationCodeCallbackServlet {

  private static Logger LOG = Logger.getLogger(GoogleAuthenticationCallbackServlet.class.getName());

  private final SessionManager sessionManager;
  private final WelcomeRobot welcomeBot;
  private final GoogleAuthentication authentication;

  @Inject
  public GoogleAuthenticationCallbackServlet(
      SessionManager sessionManager,
      WelcomeRobot welcomeBot,
      GoogleAuthentication authentication) {
    this.sessionManager = sessionManager;
    this.welcomeBot = welcomeBot;
    this.authentication = authentication;
  }

  @Override
  protected void onSuccess(HttpServletRequest req, HttpServletResponse resp, Credential credential) throws ServletException, IOException {
    try {
      ParticipantId participant = ParticipantId.of(GoogleAuthentication.getClientEmail(credential.getAccessToken()));
      HttpSession session = req.getSession(true);
      sessionManager.setLoggedInUser(session, participant);
      LOG.info("Authenticated user " + participant.getAddress());
      RegistrationUtil.createGreetingIfNotExists(participant, welcomeBot);
    } catch (InvalidParticipantAddress ex) {
      throw new IOException(ex);
    }
    GenericUrl url = new GenericUrl(req.getRequestURL().toString());
    url.setRawPath(SessionManager.SIGN_IN_URL);
    resp.sendRedirect(url.build());
  }

  @Override
  protected void onError(HttpServletRequest req, HttpServletResponse resp, AuthorizationCodeResponseUrl errorResponse) throws ServletException, IOException {
    LOG.severe(errorResponse.getError() + ": " + errorResponse.getErrorDescription());
    GenericUrl url = new GenericUrl(req.getRequestURL().toString());
    url.setRawPath(SessionManager.SIGN_IN_URL);
    resp.sendRedirect(url.build());
  }

  @Override
  protected String getRedirectUri(HttpServletRequest hsr) throws ServletException, IOException {
    GenericUrl url = new GenericUrl(hsr.getRequestURL().toString());
    url.setRawPath(SessionManager.SIGN_IN_GOOGLE_CALLBACK_URL);
    return url.build();
  }

  @Override
  protected String getUserId(HttpServletRequest hsr) throws ServletException, IOException {
    return authentication.getClientId();
  }

  @Override
  protected AuthorizationCodeFlow initializeFlow() throws ServletException, IOException {
    return authentication.newFlow();
  }

}
