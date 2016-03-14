package org.waveprotocol.box.server.authentication;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.Details;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import org.waveprotocol.box.server.CoreSettings;

/**
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class GoogleAuthentication {

  private final String clientId;
  private final String clientSecret;

  private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
  private static final HttpRequestFactory HTTP_REQUEST_FACTORY = HTTP_TRANSPORT.createRequestFactory();
  private static final JsonFactory JSON_FACTORY = new JacksonFactory();
  private static final List<String> SCOPES = Arrays.asList(
      "https://www.googleapis.com/auth/userinfo.profile",
      "https://www.googleapis.com/auth/userinfo.email");

  @Inject
  public GoogleAuthentication(@Named(CoreSettings.GOOGLE_CLIENT_ID) String clientId,
      @Named(CoreSettings.GOOGLE_CLIENT_SECRET) String clientSecret) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  public String getClientId() {
    return clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public static HttpRequestFactory getHttpRequestFactory() {
    return HTTP_REQUEST_FACTORY;
  }

  public GoogleClientSecrets getClientSecrets() {
    GoogleClientSecrets secrets = new GoogleClientSecrets();
    Details details = new GoogleClientSecrets.Details();
    details.setClientId(clientId);
    details.setClientSecret(clientSecret);
    secrets.setWeb(details);
    return secrets;
  }

  public GoogleAuthorizationCodeFlow newFlow() throws IOException {
    return new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
        getClientSecrets(), SCOPES).build();
  }

  public static String getClientEmail(String accessToken) throws IOException {
    /*
     * Get loggined user info as described in
     * https://developers.google.com/accounts/docs/OAuth2Login#userinfocall
     */
    GenericUrl url = new GenericUrl("https://www.googleapis.com/oauth2/v1/userinfo?access_token=" + accessToken);
    HttpRequest request = HTTP_REQUEST_FACTORY.buildGetRequest(url);
    HttpResponse response = request.execute();
    if (!response.isSuccessStatusCode()) {
      throw new IOException(response.getStatusMessage());
    }
    JsonElement jsonElement = new JsonParser().parse(new InputStreamReader(response.getContent(), "utf-8"));
    JsonObject jsonObj = jsonElement.getAsJsonObject();
    return jsonObj.get("email").getAsString();
  }
}
