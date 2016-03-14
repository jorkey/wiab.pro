/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.waveprotocol.box.server.rpc;

import com.google.gxp.base.GxpContext;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Locale;
import javax.inject.Singleton;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.authentication.HttpRequestBasedCallbackHandler;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.gxp.ChangePasswordPage;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Servlet allows users to change password.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
@SuppressWarnings("serial")
@Singleton
public final class ChangePasswordServlet extends HttpServlet {

  private final AccountStore accountStore;
  private final String domain;
  private final String analyticsAccount;

  private final Log LOG = Log.get(ChangePasswordServlet.class);

  @Inject
  public ChangePasswordServlet(AccountStore accountStore,
      @Named(CoreSettings.WAVE_SERVER_DOMAIN) String domain,
      @Named(CoreSettings.ANALYTICS_ACCOUNT) String analyticsAccount) {
    this.accountStore = accountStore;
    this.domain = domain;
    this.analyticsAccount = analyticsAccount;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    writeRegistrationPage("", AuthenticationServlet.RESPONSE_STATUS_NONE, req.getLocale(), resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    req.setCharacterEncoding("UTF-8");

    String responseType;
    String message = changePassword(req.getParameter(HttpRequestBasedCallbackHandler.ADDRESS_FIELD),
                  req.getParameter(HttpRequestBasedCallbackHandler.OLD_PASSWORD_FIELD),
                  req.getParameter(HttpRequestBasedCallbackHandler.PASSWORD_FIELD));

    if (message != null) {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      responseType = AuthenticationServlet.RESPONSE_STATUS_FAILED;
    } else {
      message = "Password was changed.";
      resp.setStatus(HttpServletResponse.SC_OK);
      responseType = AuthenticationServlet.RESPONSE_STATUS_SUCCESS;
      req.getSession().invalidate();
    }

    writeRegistrationPage(message, responseType, req.getLocale(), resp);
  }

  /**
   * Try to change user's password. On error,
   * returns a string containing an error message. On success, returns null.
   */
  private String changePassword(String username, String oldPassword, String password) {
    ParticipantId id;

    try {
      // First, some cleanup on the parameters.
      if (username == null) {
        return "Username portion of address cannot be empty";
      }
      username = username.trim().toLowerCase();
      if (username.contains(ParticipantId.DOMAIN_PREFIX)) {
        id = ParticipantId.of(username);
      } else {
        id = ParticipantId.of(username + ParticipantId.DOMAIN_PREFIX + domain);
      }
      if (id.getAddress().indexOf("@") < 1) {
        return "Username portion of address cannot be empty";
      }
    } catch (InvalidParticipantAddress e) {
      return "Invalid username";
    }

    AccountData account;
    try {
      account = accountStore.getAccount(id);
      if (account == null) {
        return "No such account " + username;
      }
      if (!account.asHuman().getPasswordDigest().verify(oldPassword.toCharArray())) {
        return "Old password is invalid";
      }
    } catch (PersistenceException e) {
      LOG.severe("Failed to retreive account data for " + id, e);
      return "An unexpected error occured while trying to retrieve account status";
    }

    if (password == null) {
      password = "";
    }

    account.asHuman().setPasswordDigest(new PasswordDigest(password.toCharArray()));
    try {
      accountStore.putAccount(account);
    } catch (PersistenceException e) {
      LOG.severe("Failed to write account info for " + id, e);
      return "An unexpected error occured while trying to write the account";
    }
    return null;
  }

  private void writeRegistrationPage(String message, String responseType, Locale locale,
      HttpServletResponse dest) throws IOException {
    dest.setCharacterEncoding("UTF-8");
    dest.setContentType("text/html;charset=utf-8");
    ChangePasswordPage.write(dest.getWriter(), new GxpContext(locale), domain, message,
        responseType, analyticsAccount);
  }
}
