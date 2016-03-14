package org.waveprotocol.box.server.rpc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.wave.api.SearchResult.Digest;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.authentication.AccountStoreHolder;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.persistence.html.WaveHtmlStore;
import org.waveprotocol.box.server.rpc.render.HtmlRenderer;
import org.waveprotocol.box.server.rpc.render.account.impl.ProfileImpl;
import org.waveprotocol.box.server.rpc.render.web.template.Templates;
import org.waveprotocol.box.server.search.SearchProvider;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.waveref.InvalidWaveRefException;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.jvm.JavaWaverefEncoder;

/**
 * @author vega113@gmail.com (Yuri Zelikov)
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
@SuppressWarnings("serial")
@Singleton
public class RenderSharedWaveServlet extends HttpServlet {

  private final WaveHtmlStore htmlStore;
  private final Templates templates;
  private final SearchProvider searchProvider;
  private final SessionManager sessionManager;
  private final String httpAddress;
  private final String analyticAccount;

  @Inject
  public RenderSharedWaveServlet(WaveHtmlStore htmlStore, Templates templates,
      SearchProvider searchProvider, SessionManager sessionManager,
      @Named(CoreSettings.HTTP_FRONTEND_PUBLIC_ADDRESS) String httpAddress,
      @Named(CoreSettings.ANALYTICS_ACCOUNT) String analyticsAccount) {
    this.htmlStore = htmlStore;
    this.templates = templates;
    this.searchProvider = searchProvider;
    this.sessionManager = sessionManager;
    this.httpAddress = httpAddress;
    this.analyticAccount = analyticsAccount;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
 throws ServletException,
      IOException {
    ParticipantId user = sessionManager.getLoggedInUser(req.getSession(false));
    resp.setContentType("text/html; charset=UTF-8");
    PrintWriter w = resp.getWriter();
    StringBuilder out = new StringBuilder();
    String waveRefStringValue = req.getRequestURI().replace("/render/wave/", "");
    if (!waveRefStringValue.isEmpty()) {
      WaveRef waveRef;
      try {
        waveRef = JavaWaverefEncoder.decodeWaveRefFromPath(waveRefStringValue);
        if (waveRef.getWaveletId() == null) {
          waveRef = JavaWaverefEncoder.decodeWaveRefFromPath(waveRefStringValue + "/~/conv+root");
        }
      } catch (InvalidWaveRefException e) {
        out.append(HtmlRenderer.NO_CONVERSATIONS);
        w.print(out.toString());
        w.flush();
        return;
      }
      WaveId waveId = waveRef.getWaveId();

      Digest digest = searchProvider.findWave(waveId, null);
      String innerHtml = null;
      if (digest != null) {
        innerHtml = htmlStore.readHtml(waveId);
      }
      if (innerHtml == null) {
        innerHtml = HtmlRenderer.NO_CONVERSATIONS;
      }
      String userIdStr =
          ProfileImpl.coverName(user != null ? user.getAddress() : "@" + AccountStoreHolder.getDefaultDomain());
      String link = "http://" + httpAddress + req.getRequestURI();
      String indexLink = "http://" + httpAddress + "/render/index.html";
      String outerHtml =
          templates.process(Templates.OUTER_TEMPLATE, new String[] {userIdStr, innerHtml, waveRefStringValue, Templates.GA_FRAGMENT(analyticAccount), link, indexLink});
      out.append(outerHtml);
      w.print(out.toString());
      w.flush();
    }
  }
}
