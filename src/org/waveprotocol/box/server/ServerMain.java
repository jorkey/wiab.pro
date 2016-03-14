/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.server;

import com.google.gwt.logging.server.RemoteLoggingServiceImpl;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.google.wave.api.data.ElementSerializer;

import org.waveprotocol.box.server.attachment.RobotAttachmentProvider;
import org.waveprotocol.box.server.authentication.AccountStoreHolder;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.contact.ContactsBusSubscriber;
import org.waveprotocol.box.server.executor.ExecutorsModule;
import org.waveprotocol.box.server.frontend.WaveClientServerImpl;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.ContactStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.PersistenceModule;
import org.waveprotocol.box.server.persistence.SignerInfoStore;
import org.waveprotocol.box.server.robots.RobotApiModule;
import org.waveprotocol.box.server.robots.RobotRegistrationServlet;
import org.waveprotocol.box.server.robots.active.ActiveApiServlet;
import org.waveprotocol.box.server.robots.agent.passwd.PasswordAdminRobot;
import org.waveprotocol.box.server.robots.agent.passwd.PasswordRobot;
import org.waveprotocol.box.server.robots.agent.registration.RegistrationRobot;
import org.waveprotocol.box.server.robots.agent.welcome.WelcomeRobot;
import org.waveprotocol.box.server.robots.dataapi.DataApiOAuthServlet;
import org.waveprotocol.box.server.robots.dataapi.DataApiServlet;
import org.waveprotocol.box.server.robots.passive.RobotsGateway;
import org.waveprotocol.box.server.rpc.AttachmentInfoServlet;
import org.waveprotocol.box.server.rpc.AttachmentServlet;
import org.waveprotocol.box.server.rpc.AuthenticationServlet;
import org.waveprotocol.box.server.rpc.ChangePasswordServlet;
import org.waveprotocol.box.server.rpc.FetchContactsServlet;
import org.waveprotocol.box.server.rpc.FetchProfilesServlet;
import org.waveprotocol.box.server.rpc.FolderServlet;
import org.waveprotocol.box.server.rpc.GadgetProviderServlet;
import org.waveprotocol.box.server.rpc.GadgetProxyServlet;
import org.waveprotocol.box.server.rpc.GoogleAuthenticationCallbackServlet;
import org.waveprotocol.box.server.rpc.GoogleAuthenticationServlet;
import org.waveprotocol.box.server.rpc.InitSeensWavelet;
import org.waveprotocol.box.server.rpc.LocaleServlet;
import org.waveprotocol.box.server.rpc.NotificationServlet;
import org.waveprotocol.box.server.rpc.RemakeContactsServlet;
import org.waveprotocol.box.server.rpc.RemakeHtmlServlet;
import org.waveprotocol.box.server.rpc.RemakeIndexServlet;
import org.waveprotocol.box.server.rpc.RemakeStoreIndexServlet;
import org.waveprotocol.box.server.rpc.RobotsServlet;
import org.waveprotocol.box.server.rpc.SearchServlet;
import org.waveprotocol.box.server.rpc.SearchesServlet;
import org.waveprotocol.box.server.rpc.ServerRpcProvider;
import org.waveprotocol.box.server.rpc.SignOutServlet;
import org.waveprotocol.box.server.rpc.SitemapServlet;
import org.waveprotocol.box.server.rpc.UserRegistrationServlet;
import org.waveprotocol.box.server.rpc.WaveClientServlet;
import org.waveprotocol.box.server.rpc.WaveRefServlet;
import org.waveprotocol.box.server.rpc.render.WaveHtmlRendererBusSubscriber;
import org.waveprotocol.box.server.search.SearchBusSubscriber;
import org.waveprotocol.box.server.shutdown.ShutdownManager;
import org.waveprotocol.box.server.shutdown.ShutdownPriority;
import org.waveprotocol.box.server.shutdown.Shutdownable;
import org.waveprotocol.box.server.stat.RequestScopeFilter;
import org.waveprotocol.box.server.stat.StatuszServlet;
import org.waveprotocol.box.server.stat.TimingFilter;
import org.waveprotocol.box.server.waveserver.WaveBus;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.stat.StatService;
import org.waveprotocol.wave.crypto.CertPathStore;
import org.waveprotocol.wave.federation.FederationSettings;
import org.waveprotocol.wave.federation.FederationTransport;
import org.waveprotocol.wave.federation.noop.NoOpFederationModule;
import org.waveprotocol.wave.federation.xmpp.XmppFederationModule;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.util.logging.Log;
import org.waveprotocol.wave.util.settings.SettingsBinder;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.configuration.ConfigurationException;

/**
 * Wave Server entrypoint.
 */
public class ServerMain {
  /**
   * This is the name of the system property used to find the server config file.
   */
  private static final String PROPERTIES_FILE_KEY = "wave.server.config";

  private static final Log LOG = Log.get(ServerMain.class);

  public static void main(String... args) {
    try {
      Module coreSettings = SettingsBinder.bindSettings(PROPERTIES_FILE_KEY, CoreSettings.class);
      run(coreSettings);
    } catch (PersistenceException e) {
      LOG.severe("PersistenceException when running server:", e);
    } catch (ConfigurationException e) {
      LOG.severe("ConfigurationException when running server:", e);
    } catch (WaveServerException e) {
      LOG.severe("WaveServerException when running server:", e);
    }
  }

  public static void run(Module coreSettings) throws PersistenceException,
      ConfigurationException, WaveServerException {
    setupJettyLogging();
    Injector injector = Guice.createInjector(coreSettings);
    Module profilingModule = injector.getInstance(StatModule.class);
    ExecutorsModule executorsModule = injector.getInstance(ExecutorsModule.class);
    injector = injector.createChildInjector(profilingModule, executorsModule);

    boolean enableFederation = injector.getInstance(Key.get(Boolean.class,
        Names.named(CoreSettings.ENABLE_FEDERATION)));
    if (enableFederation) {
      Module federationSettings =
          SettingsBinder.bindSettings(PROPERTIES_FILE_KEY, FederationSettings.class);
      // This MUST happen first, or bindings will fail if federation is enabled.
      injector = injector.createChildInjector(federationSettings);
    }

    Module serverModule = injector.getInstance(ServerModule.class);
    PersistenceModule persistenceModule = injector.getInstance(PersistenceModule.class);
    Module robotApiModule = new RobotApiModule();
    Module federationModule = buildFederationModule(injector, enableFederation);
    Module searchModule = injector.getInstance(SearchModule.class);
    Module htmlModule = injector.getInstance(HtmlModule.class);
    Module contactsModule = injector.getInstance(ContactsModule.class);
    injector = injector.createChildInjector(serverModule, persistenceModule, robotApiModule,
        federationModule, searchModule, htmlModule, contactsModule);

    ServerRpcProvider server = injector.getInstance(ServerRpcProvider.class);
    WaveBus waveBus = injector.getInstance(WaveBus.class);

    String domain =
      injector.getInstance(Key.get(String.class, Names.named(CoreSettings.WAVE_SERVER_DOMAIN)));
    if (!ParticipantIdUtil.isDomainAddress(ParticipantIdUtil.makeDomainAddress(domain))) {
      throw new WaveServerException("Invalid wave domain: " + domain);
    }

    initializeServer(injector, domain);
    initializeServlets(injector, server);
    initializeRobotAgents(injector, server);
    initializeRobots(injector, waveBus);
    initializeRobotAttachmentDataProvider(injector);
    initializeFrontend(injector, server);
    initializeFederation(injector);
    initializeSearch(injector, waveBus);
    initializeHtml(injector, waveBus);
    initializeContacts(injector, waveBus);

    LOG.info("Starting server");
    server.startWebSocketServer(injector);

    initializeShutdownHandler(server);
  }

  private static Module buildFederationModule(Injector settingsInjector, boolean enableFederation)
      throws ConfigurationException {
    Module federationModule;
    if (enableFederation) {
      federationModule = settingsInjector.getInstance(XmppFederationModule.class);
    } else {
      federationModule = settingsInjector.getInstance(NoOpFederationModule.class);
    }
    return federationModule;
  }

  private static void initializeServer(Injector injector, String waveDomain)
      throws PersistenceException, WaveServerException {
    AccountStore accountStore = injector.getInstance(AccountStore.class);
    accountStore.initializeAccountStore();
    AccountStoreHolder.init(accountStore, waveDomain);

    ContactStore contactStore = injector.getInstance(ContactStore.class);
    contactStore.initializeContactStore();

    // Initialize the SignerInfoStore.
    CertPathStore certPathStore = injector.getInstance(CertPathStore.class);
    if (certPathStore instanceof SignerInfoStore) {
      ((SignerInfoStore)certPathStore).initializeSignerInfoStore();
    }

    // Initialize the server.
    WaveletProvider waveServer = injector.getInstance(WaveletProvider.class);
    waveServer.initialize();
  }

  private static void initializeServlets(Injector injector, ServerRpcProvider server) {
    server.addServlet("/gadget/gadgetlist", GadgetProviderServlet.class);

    server.addServlet(AttachmentServlet.ATTACHMENT_URL + "/*", AttachmentServlet.class);
    server.addServlet(AttachmentServlet.THUMBNAIL_URL + "/*", AttachmentServlet.class);
    server.addServlet(AttachmentInfoServlet.ATTACHMENTS_INFO_URL, AttachmentInfoServlet.class);

    server.addServlet(SessionManager.SIGN_IN_URL, AuthenticationServlet.class);
    server.addServlet(SessionManager.SIGN_IN_GOOGLE_URL, GoogleAuthenticationServlet.class);
    server.addServlet(SessionManager.SIGN_IN_GOOGLE_CALLBACK_URL, GoogleAuthenticationCallbackServlet.class);
    server.addServlet("/auth/signout", SignOutServlet.class);
    server.addServlet("/auth/register", UserRegistrationServlet.class);
    server.addServlet("/auth/change_password", ChangePasswordServlet.class);

    server.addServlet("/search/*", SearchServlet.class);
    server.addServlet("/searches", SearchesServlet.class);
    server.addServlet("/locale/*", LocaleServlet.class);
    server.addServlet("/notification/*", NotificationServlet.class);

    server.addServlet("/robot/dataapi", DataApiServlet.class);
    server.addServlet(DataApiOAuthServlet.DATA_API_OAUTH_PATH + "/*", DataApiOAuthServlet.class);
    server.addServlet("/robot/dataapi/rpc", DataApiServlet.class);
    server.addServlet("/robot/register/*", RobotRegistrationServlet.class);
    server.addServlet("/robot/rpc", ActiveApiServlet.class);
    server.addServlet("/webclient/remote_logging", RemoteLoggingServiceImpl.class);
    server.addServlet("/profile/*", FetchProfilesServlet.class);
    server.addServlet("/contact/*", FetchContactsServlet.class);
    server.addServlet("/waveref/*", WaveRefServlet.class);
    server.addServlet("/folder/*", FolderServlet.class);
    //server.addServlet("/render/wavelist", WavelistRenderServlet.class);
    //server.addServlet("/render/wave/*", RenderSharedWaveServlet.class);
    server.addServlet("/robots.txt", RobotsServlet.class);
    server.addServlet("/sitemap.txt", SitemapServlet.class);

    server.addServlet("/remake_index", RemakeIndexServlet.class);
    server.addServlet("/remake_html", RemakeHtmlServlet.class);
    server.addServlet("/remake_contacts", RemakeContactsServlet.class);
    server.addServlet("/remake_store_index/*", RemakeStoreIndexServlet.class);
    server.addServlet("/init_seens", InitSeensWavelet.class);

    String gadgetServerHostname =
        injector.getInstance(Key.get(String.class, Names.named(CoreSettings.GADGET_SERVER_HOSTNAME)) );
    int gadgetServerPort =
        injector.getInstance(Key.get(Integer.class, Names.named(CoreSettings.GADGET_SERVER_PORT)) );
    Map<String, String> initParams =
        Collections.singletonMap("HostHeader", gadgetServerHostname + ":" + gadgetServerPort);
    server.addServlet("/gadgets/*", GadgetProxyServlet.class, initParams);

    server.addServlet("/", WaveClientServlet.class);

    // Profiling
    server.addFilter("/*", RequestScopeFilter.class);
    boolean enableProfiling =
        injector.getInstance(Key.get(Boolean.class, Names.named(CoreSettings.ENABLE_PROFILING)));
    if (enableProfiling) {
      server.addFilter("/*", TimingFilter.class);
      server.addServlet(StatService.STAT_URL, StatuszServlet.class);
    }
  }

  private static void initializeRobots(Injector injector, WaveBus waveBus) {
    RobotsGateway robotsGateway = injector.getInstance(RobotsGateway.class);
    waveBus.subscribe(robotsGateway);
  }

  private static void initializeRobotAgents(Injector injector, ServerRpcProvider server) {
    server.addServlet(PasswordRobot.ROBOT_URI + "/*", PasswordRobot.class);
    server.addServlet(PasswordAdminRobot.ROBOT_URI + "/*", PasswordAdminRobot.class);
    server.addServlet(WelcomeRobot.ROBOT_URI + "/*", WelcomeRobot.class);
    server.addServlet(RegistrationRobot.ROBOT_URI + "/*", RegistrationRobot.class);
  }

  private static void initializeRobotAttachmentDataProvider(Injector injector) {
    RobotAttachmentProvider apiAttachmentProvider = injector.getInstance(RobotAttachmentProvider.class);
    ElementSerializer.setAttachmentDataProvider(apiAttachmentProvider);
  }

  private static void initializeFrontend(Injector injector, ServerRpcProvider server) throws WaveServerException {
    WaveClientServerImpl rpcImpl = injector.getInstance(WaveClientServerImpl.class);
    rpcImpl.registerServices(server);
  }

  private static void initializeFederation(Injector injector) {
    FederationTransport federationManager = injector.getInstance(FederationTransport.class);
    federationManager.startFederation();
  }

  private static void initializeSearch(Injector injector, WaveBus waveBus)
      throws WaveletStateException, WaveServerException {
    WaveBus.Subscriber subscriber = injector.getInstance(SearchBusSubscriber.class);
    waveBus.subscribe(subscriber);
  }

  private static void initializeHtml(Injector injector, WaveBus waveBus)
      throws WaveletStateException, WaveServerException {
    WaveBus.Subscriber subscriber = injector.getInstance(WaveHtmlRendererBusSubscriber.class);
    waveBus.subscribe(subscriber);
  }

  private static void initializeContacts(Injector injector, WaveBus waveBus)
      throws WaveletStateException, WaveServerException {
    WaveBus.Subscriber subscriber = injector.getInstance(ContactsBusSubscriber.class);
    waveBus.subscribe(subscriber);
  }

  private static void initializeShutdownHandler(final ServerRpcProvider server) {
    ShutdownManager.getInstance().register(new Shutdownable() {

      @Override
      public void shutdown() throws Exception {
        server.stopServer();
      }
    }, ServerMain.class.getSimpleName(), ShutdownPriority.Server);
  }

  private static void setupJettyLogging() {
    Properties properties = new Properties();
    properties.setProperty("org.eclipse.jetty.LEVEL", "WARN");
    org.eclipse.jetty.util.log.StdErrLog.setProperties(properties);
 }
}