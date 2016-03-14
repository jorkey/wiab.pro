package org.waveprotocol.box.server.rpc;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.extensions.servlet.auth.oauth2.AbstractAuthorizationCodeServlet;
import com.google.api.client.http.GenericUrl;
import com.google.inject.Inject;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import org.waveprotocol.box.server.authentication.GoogleAuthentication;
import org.waveprotocol.box.server.authentication.SessionManager;

/**
 * @author akaplanov@gamil.com (Andrew Kaplanov)
 */
public class GoogleAuthenticationServlet extends AbstractAuthorizationCodeServlet {
  private final GoogleAuthentication authentication;

  @Inject
  public GoogleAuthenticationServlet(GoogleAuthentication authentication) {
    this.authentication = authentication;
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
