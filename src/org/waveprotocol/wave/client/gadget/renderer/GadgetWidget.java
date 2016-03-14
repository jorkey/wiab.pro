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

package org.waveprotocol.wave.client.gadget.renderer;

import com.google.common.annotations.VisibleForTesting;
import com.google.gwt.core.client.Duration;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.ScriptElement;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.Random;
import com.google.gwt.user.client.Window.Location;

import org.waveprotocol.box.webclient.client.ClientEvents;
import org.waveprotocol.box.webclient.client.events.WaveSelectionEvent;
import org.waveprotocol.box.webclient.flags.Flags;
import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.client.common.util.UserAgent;
import org.waveprotocol.wave.client.editor.content.AnnotationPainter;
import org.waveprotocol.wave.client.editor.content.CMutableDocument;
import org.waveprotocol.wave.client.editor.content.ContentElement;
import org.waveprotocol.wave.client.editor.content.ContentNode;
import org.waveprotocol.wave.client.gadget.GadgetLog;
import org.waveprotocol.wave.client.gadget.StateMap;
import org.waveprotocol.wave.client.gadget.StateMap.Each;
import org.waveprotocol.wave.client.scheduler.Scheduler.IncrementalTask;
import org.waveprotocol.wave.client.scheduler.Scheduler.Task;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.client.scheduler.TimerService;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.document.util.Point;
import org.waveprotocol.wave.model.document.util.XmlStringBuilder;
import org.waveprotocol.wave.model.gadget.GadgetConstants;
import org.waveprotocol.wave.model.gadget.GadgetXmlUtil;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.supplement.ObservableSupplementedWave;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.ReadableStringMap.ProcV;
import org.waveprotocol.wave.model.util.ReadableStringSet;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.waveref.WaveRef;

import java.util.Date;
import java.util.List;

/**
 * Class to implement gadget widgets rendered in the client.
 *
 * TODO(user): Modularize the gadget APIs (base, Podium, Wave, etc).
 * TODO(user): Refactor the common RPC call code.
 */
public class GadgetWidget {

  private static final String DEFAULT_HEIGHT = "100px";
  private static final String DEFAULT_WIDTH = "99%";

  /**
   * Helper class to analyze element changes in the gadget state and prefs.
   */
  private abstract class ElementChangeTask {
    /**
     * Runs processChange() wrapped in code that detects and submits changes in
     * the gadget state and prefs.
     *
     * @param node The node being processed or null if not defined.
     */
    void run(ContentNode node) {
      if (!isActive()) {
        log("Element change event in removed node: ignoring.");
        return;
      }
      log("GW.ElementChangeTask.run");
      StateMap oldState = StateMap.create();
      oldState.copyFrom(state);
      final StateMap oldPrefs = StateMap.create();
      oldPrefs.copyFrom(userPrefs);
      processChange(node);
      if (!state.compare(oldState)) {
        gadgetStateSubmitter.submit();
      }
      // TODO(user): Optimize prefs updates.
      if (!userPrefs.compare(oldPrefs)) {
        userPrefs.each(new StateMap.Each() {
          
          @Override
          public void apply(String key, String value) {
            if (!oldPrefs.has(key) || !value.equals(oldPrefs.get(key))) {
              setGadgetPref(key, value);
            }
          }
        });
      }
    }

    /**
     * Processes the changes in the elements.
     *
     * @param node The node being processed or null if not defined.
     */
    abstract void processChange(ContentNode node);
  }

  /**
   * Podium state is stored as a part of the wave gadget state and can be
   * visible to the Gadget via both Wave and Podium RPC interfaces.
   */
  private static final String PODIUM_STATE_NAME = "podiumState";

  /**
   * Gadget name prefix: the common part of the gadget IFrame ID and name. The
   * numeric gadget ID is appended to this prefix.
   */
  static final String GADGET_NAME_PREFIX = "wgadget_iframe_";

  /** Primary view for gadgets. */
  static final String GADGET_PRIMARY_VIEW = "canvas";

  /** Default view for gadgets. */
  static final String GADGET_DEFAULT_VIEW = "default";

  /**
   * Time in milliseconds to wait for the RPC script to load before logging a warning.
   */
  private static final int GADGET_RPC_LOAD_WARNING_TIMEOUT_MS = 30000;

  /** Time granularity to check for the Gadget RPC library load state. */
  private static final int GADGET_RPC_LOAD_TIMER_MS = 250;

  /** Blip submit delay in milliseconds. */
  private static final int BLIP_SUBMIT_TIMEOUT_MS = 30;

  /** Gadget state send delay in milliseconds. */
  private static final int STATE_SEND_TIMEOUT_MS = 30;

  /** The Wave API version supported by the gadget container. */
  private static final String WAVE_API_VERSION = "1";

  /** The key for the playback state in the wave gadget state map. */
  private static final String PLAYBACK_MODE_KEY = "${playback}";

  /** The key for the edit state in the wave gadget state map. */
  private static final String EDIT_MODE_KEY = "${edit}";

  /** Delay before sending one more participant information update in ms. */
  private static final int REPEAT_PARTICIPANT_INFORMATION_SEND_DELAY_MS = 5000;

  /**  Object that manages Gadget UI HTML elements. */
  private GadgetWidgetUi ui;

  /** Gadget title element. */
  private GadgetElementChild titleElement;

  /** The gadget spec URL. */
  private String source;

  /** Gadget instance ID counter (local for each client). */
  private static int nextClientInstanceId = 0;

  /** Gadget instance ID. Non-final for testing. */
  private int clientInstanceId;

  /** Gadget iframe URL. */
  private String iframeUrl;

  /** Gadget RPC token. */
  private final String rpcToken;

  /** Gadget user preferences. */
  private GadgetUserPrefs userPrefs;

  /**
   * Gadget state element map. Maps state keys to the corresponding elements.
   */
  private final StringMap<GadgetElementChild> prefElements;

  /**
   * Widget active flag: true after the widget is created, false after it is destroyed.
   */
  private boolean active = false;

  /** ID of the gadget's wave/let. */
  private WaveletName waveletName;

  /** Host blip of this gadget. */
  private ConversationBlip blip;

  /** Blip submitter. */
  private Submitter blipSubmitter;

  /** Gadget state submitter. */
  private Submitter gadgetStateSubmitter;

  /** Private gadget state submitter. */
  private Submitter privateGadgetStateSubmitter;

  /** ContentElement in the wave that corresponds to this gadget. */
  private ContentElement element;

  /** Indicator for gadget's blip editing state. */
  private EditingIndicator editingIndicator;

  /** Participant information. */
  private ParticipantInformation participants;

  /** Gadget state. */
  private StateMap state;

  /** User id of the current logged in user. */
  private String loginName;

  /**
   * Gadget state element map. Maps state keys to the corresponding elements.
   */
  private final StringMap<GadgetElementChild> stateElements;

  /** Indicates whether the gadget is known to support the Wave API. */
  private boolean waveEnabled = false;

  /** Per-user wavelet to store private gadget data. */
  private ObservableSupplementedWave supplement;

  /** Provides profile information. */
  private ProfileManager profileManager;

  /** Wave client locale. */
  private Locale locale;

  /** Gadget library initialization flag. */
  private static boolean initialized = false;

  /**
   * Gadget element child that defines what nodes to check for redundancy in the
   * removeRedundantNodeTask. Only a single task can be scheduled at a time.
   */
  private GadgetElementChild redundantNodeCheckChild = null;

  /**
   * Indicates whether the gadget has performed a document mutation on behalf of
   * the user. This flag is checked when the gadget tries to perform
   * non-essential modifications of the document such as duplicate node cleanup
   * or height attribute update. Performing such operations may generate
   * unnecessary playback frames and attribute modifications to a user who did
   * not use the gadget. The flag is set when the gadget modifies state, prefs,
   * title, or any other elements that normally are linked to user actions in
   * the gadget.
   */
  private boolean documentModified = false;

  /**
   * Indicates that the iframe URL attribute should be updated when the gadget
   * modifies the document in response to a user action.
   */
  private boolean toUpdateIframeUrl = false;

  /**
   * Indicates that the width attribute should be updated when the gadget
   * modifies the document in response to a user action.
   */
  private boolean toUpdateWidth = false;

  /**
   * Indicates that the height attribute should be updated when the gadget
   * modifies the document in response to a user action.
   */
  private boolean toUpdateHeight = false;

  private final String clientInstanceLogLabel;

  // Note that the following regex expressions are strings rather than compiled patterns because GWT
  // does not (yet) support those. Consider using the new GWT RegExp class in the future.

  /**
   * Pattern to match rpc token, security token, and user preference parameters
   * in a URL fragment. Used to remove all these parameters.
   */
  private final static String FRAGMENT_CLEANING_PATTERN = "(^|&)(rpctoken=|st=|up_)[^&]*";

  /**
   * Pattern to match module ID and security token parameters a URL. Used to
   * remove all these parameters.
   */
  private final static String URL_CLEANING_PATTERN = "&(mid=|st=|lang=|country=|debug=)[^&]*";

  /**
   * Pattern to match and remove URL fragment including the #.
   */
  private final static String FRAGMENT_PATTERN = "#.*";

  /**
   * Pattern to match and remove URL part before fragment including the #.
   */
  private final static String BEFORE_FRAGMENT_PATTERN = "[^#]*#";

  /**
   * Pattern to validate URL fragment.
   */
  private final static String FRAGMENT_VALIDATION_PATTERN =
      "([\\w~!&@\\$\\-\\.\\'\\(\\)\\*\\+\\,\\;\\=\\?\\:]|%[0-9a-fA-F]{2})+";

  /**
   * Pattern to match iframe host in the beginning of a URL. This is not a
   * validation check. The user can choose their own host.  This simply serves
   * to extract the iframe segment of the URL
   */
  private final static String IFRAME_HOST_PATTERN =
      "^\\/\\/(https?:\\/\\/)?[^\\/]+\\/";

  /**
   * Pattern to remove XML-unsafe characters. Snippeting fails on some of those
   * symbol combinations due to a potential bug in XML attribute processing.
   * Theoretically all those symbols should be tolerated and displayed in
   * snippets without any special processing in this class.
   *
   * TODO(user): Investigate/test this later to remove sanitization.
   */
  private final static String SNIPPET_SANITIZER_PATTERN = "[<>\\\"\\'\\&]";

  /** Minimum gadget height; all values below are invalid. */
  private final static int MIN_GADGET_HEIGHT = 3;
  
  private Task initializePodiumTask = new Task() {

    @Override
    public void execute() {
      log("GW.initializePodiumTask is executed");      
      initializePodium();
    }
  };  
  
  /**
   * Listens to contributors/participants adding/removing.
   */
  private final GadgetWaveletListener gadgetWaveletListener = new GadgetWaveletListener() {

    @Override
    public void onBlipContributorAdded(ParticipantId contributor) {
      if (isActive()) {
        if (shouldLog()) {      
          log("Contributor added ", contributor);
        }  
        sendCurrentParticipantInformation();
      } else {
        log("Contributor added event in deleted node.");
      }
    }

    @Override
    public void onBlipContributorRemoved(ParticipantId contributor) {
      if (isActive()) {
        if (shouldLog()) {      
          log("Contributor removed ", contributor);
        }  
        sendCurrentParticipantInformation();
      } else {
        log("Contributor removed event in deleted node.");
      }
    }

    @Override
    public void onParticipantAdded(ParticipantId participant) {
      if (isActive()) {
        if (shouldLog()) {      
          log("Participant added ", participant);
        }  
        sendCurrentParticipantInformation();
      } else {
        log("Participant added event in deleted node.");
      }
    }

    @Override
    public void onParticipantRemoved(ParticipantId participant) {
      if (isActive()) {
        if (shouldLog()) {      
          log("Participant removed ", participant);
        }  
        sendCurrentParticipantInformation();
      } else {
        log("Participant removed event in deleted node.");
      }
    }
  };
  
  /**
   * Listens to gadget state change.
   */  
  private final ObservableSupplementedWave.Listener supplementedWaveListener =
      new ObservableSupplementedWave.ListenerImpl() {
        
    @Override
    public void onMaybeGadgetStateChanged(String gadgetId) {
      if (gadgetId != null) {
        String myId = getGadgetId();
        if (gadgetId.equals(myId)) {
          privateGadgetStateSubmitter.submitImmediately();
        }
      }
    }        
  };
  
  /**
   * Listens to gadget RPC calls.
   */  
  private final GadgetRpcListener gadgetRpcListener = new GadgetRpcListener() {

    @Override
    public void setTitle(String title) {    
      if (!isActive()) {
        return;
      }
      log("GW.setTitle");    
      final String newTitle = (title == null) ? "" : title;
      if (shouldLog()) {    
        log("Set title '", XmlStringBuilder.createText(newTitle), "'");
      }  
      if (titleElement == null) {
        onModifyingDocument();
        GadgetElementChild.create(element.getMutableDoc().insertXml(
            Point.end((ContentNode) element), GadgetXmlUtil.constructTitleXml(newTitle)));
        blipSubmitter.submit();
      } else {
        if (!title.equals(titleElement.getValue())) {
          onModifyingDocument();
          titleElement.setValue(newTitle);
          blipSubmitter.submit();
        }
      }
    }

    @Override
    public void setPrefs(String ... keyValue) {    
      // Ignore callbacks from the gadget in playback mode.
      if (!canModifyDocument()) {
        return;
      }
      log("GW.setPrefs");    
      // Ignore the last key if its value is missing.
      for (int i = 0; i < keyValue.length - 1; i+=2) {
        setPref(keyValue[i], keyValue[i + 1]);
      }
    }  

    @Override
    public void setIframeHeight(String height) {
      if (!isActive()) {
        return;
      }
      int h = Integer.parseInt(height);
      if (h < MIN_GADGET_HEIGHT) {
        return;
      }
      log("GW.setIframeHeight: height=" + height);    
      ui.setIframeHeight(height);
      ui.makeInline();
      if (canModifyDocument() && documentModified) {
        scheduleGadgetAttributeUpdate(GadgetConstants.LAST_KNOWN_HEIGHT_ATTRIBUTE, height);
      } else {
        toUpdateHeight = true;
      }
    }

    @Override
    public void setIframeWidth(String width) {
      if (!isActive()) {
        return;
      }
      int w = Integer.parseInt(width);
      if (w <= 0) {
        return;
      }
      log("GW.setIframeWidth: width=" + width);
      ui.setIframeWidth(width);
      ui.makeInline();
      if (canModifyDocument() && documentModified) {
        scheduleGadgetAttributeUpdate(GadgetConstants.LAST_KNOWN_WIDTH_ATTRIBUTE, width);
      } else {
        toUpdateWidth = true;
      }
    }  

    @Override
    public void requestNavigateTo(String url) {
      log("GW.requestNavigateTo: url=" + url);
      // NOTE(user): Currently only allow the gadgets to change the fragment part of the URL.
      String newFragment = url.replaceFirst(BEFORE_FRAGMENT_PATTERN, "");
      if (newFragment.matches(FRAGMENT_VALIDATION_PATTERN)) {
        Location.replace(Location.getHref().replaceFirst(FRAGMENT_PATTERN, "") + "#" + newFragment);
      } else {
        log("Navigate request denied.");
      }
    }  

    @Override
    public void updatePodiumState(String podiumState) {
      log("GW.updatePodiumState: podiumState=" + podiumState);
      if (isActive()) {
        modifyState(PODIUM_STATE_NAME, podiumState);
        blipSubmitter.submit();
      }
    }  

    @Override
    public void waveEnable(String waveApiVersion) {    
      if (!isActive()) {
        return;
      }
      log("GW.waveEnable: waveApiVersion=" + waveApiVersion);

      waveEnabled = true;      

      // HACK: See substituteIframeId() description.
      // TODO(user): Remove when the Firefox bug is fixed.
      if (UserAgent.isFirefox()) {
        substituteIframeId();
      }

      sendWaveGadgetInitialization();

      GadgetWidget.this.element.getMutableDoc().addListener(
          new CMutableDocument.EditingModeChangeListener() {

        @Override
        public void onEditingModeChanged(boolean editingMode) {
          sendMode();
        }
      });

      ui.showAsLoaded();
    }  

    @Override
    public void waveGadgetStateUpdate(final JavaScriptObject delta) {
      // Return if in playback mode. isEditable indicates playback.
      if (!canModifyDocument()) {
        return;
      }
      log("GW.waveGadgetStateUpdate");
      final StateMap deltaState = StateMap.create();
      deltaState.fromJsonObject(delta);
      // Defer state modifications to avoid RPC failure in Safari 3. The
      // intermittent failure is caused by RPC called from received RPC
      // callback.
      // TODO(user): Remove this workaround once this is fixed in GGS.
      log("GW.waveGadgetStateUpdate.Task is scheduled");
      getTimer().schedule(new Task() {

        @Override
        public void execute() {
          log("GW.waveGadgetStateUpdate.Task is executed");
          deltaState.each(new Each() {

            @Override
            public void apply(final String key, final String value) {
              if (value != null) {
                modifyState(key, value);
              } else {
                deleteState(key);
              }
            }
          });
          if (shouldLog()) {          
            log("Applied delta ", delta.toString(), " new state ", state.toJson());
          }  
          gadgetStateSubmitter.triggerScheduledSubmit();
          blipSubmitter.submitImmediately();
        }
      });
    }  

    @Override
    public void wavePrivateGadgetStateUpdate(JavaScriptObject delta) {
      // Return if in playback mode. isEditable indicates playback.
      if (!canModifyDocument()) {
        return;
      }

      StateMap deltaState = StateMap.create();
      deltaState.fromJsonObject(delta);
      final String gadgetId = getOrGenerateGadgetId();
      if (gadgetId != null) {
        deltaState.each(new Each() {

          @Override
          public void apply(final String key, final String value) {
            supplement.setGadgetState(gadgetId, key, value);
          }
        });
        if (shouldLog()) {        
          log("Applied private delta ", deltaState.toJson());
        }  
        privateGadgetStateSubmitter.triggerScheduledSubmit();
      } else {
        if (shouldLog()) {        
          log("Unable to get gadget ID to update private state. Delta ", deltaState.toJson());
        }  
      }
    }  

    @Override
    public void logMessage(String message) {
      GadgetLog.developerLog(message);
    }
    
    @Override
    public void setSnippet(String snippet) {
      if (!canModifyDocument()) {
        return;
      }
      String safeSnippet = sanitizeSnippet(snippet);
      log("Snippet changed: " + safeSnippet);
      scheduleGadgetAttributeUpdate(GadgetConstants.SNIPPET_ATTRIBUTE, safeSnippet);
    }

    @Override
    public void openWave(String waveId) {
      log("Requested open wave: ", waveId);
      ClientEvents.get().fireEvent(new WaveSelectionEvent(
          WaveRef.of(WaveId.deserialise(waveId)), false, false));
    }    
  };
  
  /**
   * Listens to gadget client UI actions.
   */
  private final GadgetUiListener gadgetUiListener = new GadgetUiListener() {

    @Override
    public void deleteGadget() {
      if (canModifyDocument()) {
        element.getMutableDoc().deleteNode(element);
      }
    }

    @Override
    public void selectGadget() {
      if (isActive()) {
        CMutableDocument doc = element.getMutableDoc();
        element.getSelectionHelper().setSelectionPoints(
            Point.before(doc, element), Point.after(doc, element));
      }
    }

    @Override
    public void resetGadget() {
      log("GW.resetGadget");
      if (canModifyDocument()) {
        state.each(new Each() {

          @Override
          public void apply(String key, String value) {
            deleteState(key);
          }
        });

        gadgetStateSubmitter.submit();
        final String gadgetId = getGadgetId();
        if (gadgetId != null) {
          supplement.getGadgetState(gadgetId).each(new ProcV<String>() {

            @Override
            public void apply(String key, String value) {
              supplement.setGadgetState(gadgetId, key, null);
            }
          });
          privateGadgetStateSubmitter.submit();
        }
      }
    }    
  };
  
  /**
   * Constructs GadgetWidget for testing.
   */
  private GadgetWidget() {
    clientInstanceId = nextClientInstanceId++;
    clientInstanceLogLabel = "[" + clientInstanceId + "]";
    prefElements = CollectionUtils.createStringMap();
    stateElements = CollectionUtils.createStringMap();
    rpcToken = "" +
        ((Long.valueOf(Random.nextInt()) << 32) | (Long.valueOf(Random.nextInt()) & 0xFFFFFFFFL));
  }  
  
  private static native boolean gadgetLibraryLoaded() /*-{
    return ($wnd.gadgets && $wnd.gadgets.rpc) ? true : false;
  }-*/;

  /**
   * Preloads the libraries and initializes them on the first use.
   */
  private static void initializeGadgets() {
    GadgetLog.log("GW.initializeGadgets");
    if (!initialized && !gadgetLibraryLoaded()) {
      GadgetLog.log("Initializing Gadget RPC script tag.");
      loadGadgetRpcScript();
      initialized = true;
      GadgetLog.log("Gadgets RPC script tag has been initialized.");
    }
    // TODO(user): Remove the css hacks once CAJA is fixed.
    if (!initialized && !gadgetLibraryLoaded()) {
      // HACK(user): NOT reachable, but GWT thinks it is.
      excludeCssName();
    }
  }

  /**
   * Utility function to convert a Gadget StateMap to a string to be stored as
   * an attribute value.
   *
   * @param state JSON object to be converted to string.
   * @return string to be saved as an attribute value.
   */
  private static String stateToAttribute(StateMap state) {
    if (state == null) {
      return URL.encodeQueryString("{}");
    }
    return URL.encodeQueryString(state.toJson());
  }

  /**
   * Utility function to convert an attribute string to a Gadget StateMap.
   *
   * @param attribute attribute value string.
   * @return StateMap constructed from the attribute value.
   */
  private StateMap attributeToState(String attribute) {
    StateMap result = StateMap.create();
    if ((attribute != null) && !attribute.equals("")) {
      if (shouldLog()) {
        log("Unescaped attribute: ", URL.decodeQueryString(attribute));
      }  
      result.fromJson(URL.decodeQueryString(attribute));
      if (shouldLog()) {      
        log("State map: ", result.toJson());
      }  
    }
    return result;
  }

  /**
   * Returns the gadget name that identifies the gadget and its frame.
   *
   * @return gadget name.
   */
  private String getGadgetName() {
    return GADGET_NAME_PREFIX + clientInstanceId;
  }

  private void updatePrefsFromAttribute(String prefAttribute) {
    log("GW.updatePrefsFromAttribute");
    if (!stateToAttribute(userPrefs).equals(prefAttribute)) {
      StateMap prefState = attributeToState(prefAttribute);
      userPrefs.parse(prefState, true);
      if (shouldLog()) {
        log("Updating user prefs: ", userPrefs.toJson());
      }  
      prefState.each(new StateMap.Each() {

        @Override
        public void apply(String key, String value) {
          setGadgetPref(key, value);
        }
      });
    }
  }

  /**
   * Processes changes in the gadget element attributes.
   * TODO(user): move some of this code to the handler.
   *
   * @param name attribute name.
   * @param value new attribute value.
   */
  public void onAttributeModified(String name, String value) {
    log("GW.onAttributeModified");
    log("Attribute '", name, "' changed to '", value, "'");
    if (userPrefs == null) {
      log("Attribute changed before the gadget is initialized.");
      return;
    }

    switch (name) {
      case GadgetConstants.URL_ATTRIBUTE:
        source = (value == null) ? "" : value;
        break;
      case GadgetConstants.TITLE_ATTRIBUTE:
        String title = (value == null) ? "" : URL.decodeQueryString(value);
        if (!title.equals(ui.getTitleLabelText())) {
          log("Updating title: ", title);
          ui.setTitleLabelText(title);
        }
        break;
      case GadgetConstants.PREFS_ATTRIBUTE:
        updatePrefsFromAttribute(value);
        break;
      case GadgetConstants.STATE_ATTRIBUTE:
        StateMap newState = attributeToState(value);
        if (!state.compare(newState)) {
          String podiumState = newState.get(PODIUM_STATE_NAME);
          if ((podiumState != null) && (!podiumState.equals(state.get(PODIUM_STATE_NAME)))) {
            GadgetRpcSender.sendPodiumOnStateChangedRpc(getGadgetName(), podiumState);
          }
          state.clear();
          state.copyFrom(newState);
          if (shouldLog()) {
            log("Updating gadget state: ", state.toJson());
          }  
          gadgetStateSubmitter.submit();
        }
        break;
    }
  }

  /**
   * Loads Gadget RPC library script.
   */
  private static void loadGadgetRpcScript() {
    ScriptElement script = Document.get().createScriptElement();
    script.setType("text/javascript");
    script.setSrc(Flags.get().gadgetRpcPath());
    Document.get().getBody().appendChild(script);
  }

  /**
   * Appends tokens to the iframe URI fragment.
   *
   * @param fragment Original parameter fragment of the gadget URI.
   * @return Updated parameter fragment with new RPC and security tokens.
   */
  private String updateGadgetUriFragment(String fragment) {
    fragment = "rpctoken=" + rpcToken +
          (fragment.isEmpty() || (fragment.charAt(0) == '&') ? "" : "&") + fragment;
    return fragment;
  }

  @VisibleForTesting
  static String cleanUrl(String url) {
    GadgetLog.log("GW.cleanUrl: url=" + url);
    String baseUrl = url;
    String fragment = "";
    int fragmentIndex = url.indexOf("#");
    if (fragmentIndex >= 0) {
      fragment = (url.substring(fragmentIndex + 1)).replaceAll(FRAGMENT_CLEANING_PATTERN, "");
      if (fragment.startsWith("&")) {
        fragment = fragment.substring(1);
      }
      baseUrl = url.substring(0, fragmentIndex);
    }
    baseUrl = baseUrl.replaceAll(URL_CLEANING_PATTERN, "")  + (fragment.isEmpty() ? "" : "#" + fragment);
    GadgetLog.log("baseUrl=" + baseUrl);
    return baseUrl;
  }

  private static String removeExcessiveParametersFromUrl(String url, String... parameters) {
    for (String parameter : parameters) {
      url = url.replace("&" + parameter + "=%25" + parameter + "%25", "");
    }
    return url;
  }
  
  /**
   * Constructs IFrame URI of this gadget.
   *
   * @param url URL template.
   * @return IFrame URI of this gadget.
   */
  String buildIframeUrl(int instanceId, String url) {
    GadgetLog.log("GW.buildIframeUrl: url=" + url);    
    final StringBuilder builder =  new StringBuilder();
    String fragment = "";
    int fragmentIndex = url.indexOf("#");
    if (fragmentIndex >= 0) {
      fragment = url.substring(fragmentIndex + 1);
      url = url.substring(0, fragmentIndex);
    }
    builder.append(url);

    builder.append("&mid=").append(instanceId);
    builder.append("&lang=").append(locale.getLanguage());
    builder.append("&country=").append(locale.getCountry());
    String href = getUrlPrefix();
    // TODO(user): Parent is normally the last non-hash parameter. It is moved
    // as a temp fix for kitchensinky. Move it back when the kitchensinky is
    // working wihout this workaround.
    builder.append("&parent=").append(URL.encodeQueryString(href));
    builder.append("&wave=").append(WAVE_API_VERSION);
    builder.append("&waveId=").append(URL.encodeQueryString(
        ModernIdSerialiser.INSTANCE.serialiseWaveId(waveletName.waveId)));
    fragment = updateGadgetUriFragment(fragment);
    if (!fragment.isEmpty()) {
      builder.append("#").append(fragment);
      log("Appended fragment: ", fragment);
    }
    if (userPrefs != null) {
      userPrefs.each(new StateMap.Each() {

        @Override
        public void apply(String key, String value) {
          if (value != null) {
            builder.append("&up_").append(URL.encodeQueryString(key));
            builder.append('=').append(URL.encodeQueryString(value));
          }
        }
      });
    }
    return builder.toString();
  }

  /**
   * Verifies that the gadget has non-empty attribute.
   *
   * @param element container element.
   * @param name attribute name.
   * @return true if non-empty height attribute exists, flase otherwise.
   */
  private static boolean hasAttribute(ContentElement element, String name) {
    if (element.hasAttribute(name)) {
      String value = element.getAttribute(name);
      if (!"".equals(value)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Updates the gadget attribute in a deferred command if the panel is
   * editable.
   *
   * @param attributeName attribute name.
   * @param value new attribute value.
   */
  private void scheduleGadgetAttributeUpdate(final String attributeName, final String value) {
    log("GW.scheduleGadgetAttributeUpdate.Task is scheduled");
    getTimer().schedule(new Task() {
          
      @Override
      public void execute() {
        log("GW.scheduleGadgetAttributeUpdate.Task is executed");
        if (canModifyDocument() && documentModified) {
          String oldValue = element.getAttribute(attributeName);
          if (!value.equals(oldValue)) {
            element.getMutableDoc().setElementAttribute(element, attributeName, value);
          }
        }
      }
    });
  }

  /**
   * Updates gadget IFrame attributes.
   *
   * @param url URL template for the iframe.
   * @param width preferred width of the iframe.
   * @param height preferred height of the iframe.
   */
  private void updateGadgetIframe(String url, long width, long height) {
    log("GW.updateGadgetIframe: url=" + url + ", width=" + width + ", height=" + height);
    if (!isActive()) {
      return;
    }
    iframeUrl = url;
    gadgetRpcListener.setIframeWidth(Long.toString(width));
    gadgetRpcListener.setIframeHeight(Long.toString(height));
    String ifr = buildIframeUrl(getInstanceId(), url);
    log("ifr: ", ifr);
    ui.setIframeSource(ifr);
  }

  /**
   * Creates a display widget for the gadget.
   *
   * @param element ContentElement from the wave.
   * @param blip gadget blip.
   * @return display widget for the gadget.
   */
  public static GadgetWidget createGadgetWidget(ContentElement element, WaveletName waveletName,
      ConversationBlip blip, ObservableSupplementedWave supplement,
      ProfileManager profileManager, Locale locale, String loginName) {
    GadgetLog.log("GW.createGadgetWidget");
    final GadgetWidget widget = GWT.create(GadgetWidget.class);
    widget.element = element;
    widget.editingIndicator =
      new BlipEditingIndicator(element.getRenderedContentView().getDocumentElement());
    String width = element.getAttribute(GadgetConstants.LAST_KNOWN_WIDTH_ATTRIBUTE);
    if (width == null) {
      width = DEFAULT_WIDTH;
    }
    String height = element.getAttribute(GadgetConstants.LAST_KNOWN_HEIGHT_ATTRIBUTE);
    if (height == null) {
      height = DEFAULT_HEIGHT;
    }
    GadgetLog.log("Saved width=" + width + ", saved height=" + height);        

    widget.ui = new GadgetWidgetUi(widget.getGadgetName(), widget.editingIndicator, width, height);      
    widget.state = StateMap.create();
    initializeGadgets();
    widget.blip = blip;
    widget.initializeGadgetContainer();
    widget.ui.setGadgetUiListener(widget.gadgetUiListener);
    widget.waveletName = waveletName;
    widget.supplement = supplement;
    widget.profileManager = profileManager;
    widget.locale = locale;
    widget.loginName = loginName;
    supplement.addListener(widget.supplementedWaveListener);
    return widget;
  }

  /**
   * @return the actual GWT widget
   */
  public GadgetWidgetUi getWidget() {
    return ui;
  }

  private String sanitizeSnippet(String snippet) {
    return snippet.replaceAll(SNIPPET_SANITIZER_PATTERN, " ");
  }

  /**
   * Gets the attribute value from the mutable document associated with the
   * gadget.
   *
   * @param attributeName name of the attribute
   * @return attribute value or empty string if attribute is missing
   */
  private String getAttribute(String attributeName) {
    return element.hasAttribute(attributeName) ? element.getAttribute(attributeName) : "";
  }

  @VisibleForTesting
  static String getIframeHost(String url) {
    // Ideally this should be done with regex matcher which is not supported in GWT.
    String iframeHostMatcher = url.replaceFirst(IFRAME_HOST_PATTERN, "");
    if (iframeHostMatcher.length() != url.length()) {
      return url.substring(0, url.length() - iframeHostMatcher.length());
    } else {
      return "";
    }
  }

  /**
   * Controller registration.
   *
   * @param url URL template of the gadget iframe.
   * @param width preferred iframe width.
   * @param height preferred iframe height.
   */
  private void controllerRegistration(String url, long width, long height) {
    log("GW.controllerRegistration: url=" + url + ", width=" + width + ", height=" + height);    
    getTimer().cancel(controllerRegistrationTask);

    Controller controller = Controller.getInstance();
    String iframeHost = getIframeHost(url);
    String relayUrl = iframeHost + Flags.get().gadgetRelayPath();
    controller.setRelayUrl(getGadgetName(), relayUrl);
    controller.registerGadgetListener(getGadgetName(), gadgetRpcListener);
    controller.setRpcToken(getGadgetName(), rpcToken);
    updateGadgetIframe(url, width, height);

    // TODO(user): This is a hack to delay Podium initialization.
    // Define an initialization protocol for Podium to avoid this.
    log("GW.initializePodiumTask is scheduled, delay=3000");
    getTimer().scheduleDelayed(initializePodiumTask, 3000);
    if (shouldLog()) {      
      log("Gadget ", getGadgetName(), " is registered, relayUrl=", relayUrl, ", RPC token=",
          rpcToken);
    }  
  }

  private void registerWithController(String url, long width, long height) {
    log("GW.registerWithController: url=" + url + ", width=" + width + ", height=" + height);
    if (gadgetLibraryLoaded()) {
      controllerRegistration(url, width, height);
    } else {
      scheduleControllerRegistration(url, width, height);
    }
  }

  private String crUrl;
  private long crWidth;
  private long crHeight;
  private IncrementalTask controllerRegistrationTask = new IncrementalTask() {

    private final double loadWarningTime =
        Duration.currentTimeMillis() + GADGET_RPC_LOAD_WARNING_TIMEOUT_MS;    
    
    @Override
    public boolean execute() {
      if (!isActive()) {
        log("Not active.");
        return false;
      } else if (gadgetLibraryLoaded()) {
        controllerRegistration(crUrl, crWidth, crHeight);
        return false;
      } else {
        if (Duration.currentTimeMillis() > loadWarningTime) {
          showBrokenGadget("Gadget RPC script failed to load on time.");
          return false;
        }
      }
      return true;      
    }
  };
  
  /**
   * Registers the Gadget object as RPC event listener with the Gadget RPC
   * Controller after waiting for the Gadget RPC library to load.
   */
  private void scheduleControllerRegistration(String url, long width, long height) {
    log("GW.scheduleControllerRegistration");
  
    crUrl = url;
    crWidth = width;
    crHeight = height;
    log("GW.controllerRegistrationTask is scheduled repeating");
    getTimer().scheduleRepeating(controllerRegistrationTask, GADGET_RPC_LOAD_TIMER_MS,
        GADGET_RPC_LOAD_TIMER_MS);
  }

  private void initializeGadgetContainer() {
    userPrefs = GadgetUserPrefs.create();
    blipSubmitter = new Submitter(BLIP_SUBMIT_TIMEOUT_MS, new Submitter.SubmitTask() {

      @Override
      public void doSubmit() {
        // TODO: send a playback frame signal.
        log("GW.blipSubmitter.submitFinished");
      }
    }) {

      @Override
      public void submit() {
        log("GW.blipSubmitter.submitStarted");
        super.submit();
      }
    };
    
    gadgetStateSubmitter = new Submitter(STATE_SEND_TIMEOUT_MS, new Submitter.SubmitTask() {

      @Override
      public void doSubmit() {
        sendGadgetState();
        log("GW.gadgetStateSubmitter.submitFinished: gadget state has been sent.");
      }
    }) {
      
      @Override
      public void submit() {
        log("GW.gadgetStateSubmitter.submitStarted");
        super.submit();
      }
    };
    
    privateGadgetStateSubmitter = new Submitter(STATE_SEND_TIMEOUT_MS, new Submitter.SubmitTask() {

      @Override
      public void doSubmit() {
        sendPrivateGadgetState();
        log("GW.privateGadgetStateSubmitter.submitFinished: private gadget state has been sent.");
      }
    }) {

      @Override
      public void submit() {
        log("GW.privateGadgetStateSubmitter.submitStarted");
        super.submit();
      }      
    };
  }

  private void initializePodium() {
    if (!isActive()) {
      // If the widget does not exist, exit.
      return;
    }
    log("GW.initializePodium");
    for (ParticipantId participant : blip.getConversation().getParticipantIds()) {
      String myId = participants.getMyId();
      if ((myId != null) && !participant.getAddress().equals(myId)) {
        String opponentId = participant.getAddress();
        try {
          log("GW.sendPodiumOnInitializedRpc");
          GadgetRpcSender.sendPodiumOnInitializedRpc(getGadgetName(), myId, opponentId);
          log("Sent Podium initialization: " + myId + " " + opponentId);
          String podiumState = state.get(PODIUM_STATE_NAME);
          if (podiumState != null) {
            log("GWT.sendPodiumOnStateChangedRpc");              
            GadgetRpcSender.sendPodiumOnStateChangedRpc(getGadgetName(), podiumState);
            log("Sent Podium state update.");
          }
        } catch (Exception e) {
          // This is a catch to avoid sending RPCs to deleted gadgets.
          log("Podium initialization failure");
        }
        return;
      }
    }
    log("Podium is not initialized: less than two participants.");
  }
  
  private void constructGadgetFromUrl(String iframeUrl) {
    if (!isValidIframeUrl(iframeUrl)) {
      showBrokenGadget("Invalid IFrame URL: " + iframeUrl);      
      return;
    }
    
    log("GW.constructGadgetFromUrl: iframeUrl=" + iframeUrl);
    String url = removeExcessiveParametersFromUrl(iframeUrl,
        "view", "lang", "country", "debug", "nocache", "sanitize");
    url = cleanUrl(url);
    if (url.equals(iframeUrl)) {
      log("Received metadata matches the cached information.");
      constructGadgetSizeFromUrl(url);
      return;
    }
    // NOTE(user): Technically we should not save iframe URLs for gadgets with security tokens,
    // but some gadgets, such as YNM, that depend on opensocial libraries get security tokens they
    // never use. Also to enable gadgets in Ripple and other light Wave clients it's desirable to
    // to always have the iframe URL at least for rudimentary rendering.
    if (canModifyDocument() && documentModified) {
      scheduleGadgetAttributeUpdate(GadgetConstants.IFRAME_URL_ATTRIBUTE, url);
    } else {
      toUpdateIframeUrl = true;
    }
    constructGadgetSizeFromUrl(url);
  }

  private void constructGadgetSizeFromUrl(String url) {
    log("GW.constructGadgetSizeFromUrl: url=" + url);
    registerWithController(url, 0, 0);
    gadgetRpcListener.setIframeHeight("0");
    gadgetRpcListener.setIframeWidth("0");
  }

  /**
   * This function generates a gadget instance ID for generating gadget metadata
   * and security tokens. The ID should be 1. hard to guess; 2. same for the
   * same gadget element for the same participant in the same wave every time
   * the wave is rendered in the same client; 3. preferably, but not necessarily
   * different for different gadget elements and different participants.
   *
   * Condition 2 is needed to achieve consistent behavior in gadgets that, for
   * example, request special permissions using OAuth/OpenSocial.
   *
   * This function satisfies those conditions, except the ID is going to be
   * always the same for the same type of the gadget in the same wavelet for the
   * same participant. This poses minimal risk (in terms of matching domains and
   * security tokens) because the gadgets with matching IDs would be rendered
   * for the same person in the same wave.
   *
   * NOTE(user): Instance ID should be non-negative number to work around a
   * bug in GGS and/or Linux libraries that produces non-renderable iframe URLs
   * for negative instance IDs. The domain name starts with dash "-". Browsers
   * in Windows and Mac OS tolerate this, but browsers in Linux fail to render
   * such URLs.
   *
   * @return instance ID for the gadget.
   */
  private int getInstanceId() {
    String name = ModernIdSerialiser.INSTANCE.serialiseWaveletName(waveletName);
    String instanceDescriptor = name + loginName + source;
    int hash = instanceDescriptor.hashCode();
    return (hash < 0) ? ~hash : hash;
  }

  private void showBrokenGadget(String message) {
    ui.showBrokenGadget(message);
    log("Broken gadget: ", message);
  }  

  private boolean isValidIframeUrl(String url) {
    return (url != null) && !url.isEmpty() && !getIframeHost(url).isEmpty();
  }  
  
  private void scheduleGadgetIdUpdate() {
    log("GW.scheduleGadgetIdUpdate.Task is scheduled");
    getTimer().schedule(new Task() {
      
      @Override
      public void execute() {
        log("GW.scheduleGadgetIdUpdate.Task is executed");
        generateAndSetGadgetId();
      }
    });
  }

  private void allowModificationOfNewlyCreatedGadget() {
    // Missing height attribute indicates freshly added gadget. Assume that the
    // document is modified for the purpose of updating attributes.
    if (!hasAttribute(element, GadgetConstants.LAST_KNOWN_HEIGHT_ATTRIBUTE) &&
        editingIndicator.isEditing()) {
      scheduleGadgetIdUpdate();
      onModifyingDocument();
    }
  }

  /**
   * Creates a widget to render the gadget.
   */
  public void createWidget() {
    log("GW.createWidget");
    if (isActive()) {
      log("Repeated attempt to create gadget widget.");
      return;
    }

    active = true;
    log("Creating gadget widget: ", getGadgetName());

    ui.enableMenu();
    allowModificationOfNewlyCreatedGadget();

    source = getAttribute(GadgetConstants.URL_ATTRIBUTE);
    String title = getAttribute(GadgetConstants.TITLE_ATTRIBUTE);
    ui.setTitleLabelText((title == null) ? "" : URL.decodeQueryString(title));

    updatePrefsFromAttribute(getAttribute(GadgetConstants.PREFS_ATTRIBUTE));

    refreshParticipantInformation();          

    // HACK(anorth): This event routing should happen outside the widget.
    ObservableConversation conv = (ObservableConversation) blip.getConversation();
    WaveletListenerAdapter waveletListenerAdapter = new WaveletListenerAdapter(blip,
        gadgetWaveletListener);
    conv.addBlipContentListener(waveletListenerAdapter);
    conv.addParticipantListener(waveletListenerAdapter);

    String gadgetServerUrl = Flags.get().gadgetServerUrl();
    if (!gadgetServerUrl.isEmpty()) {
      String url = "//" + gadgetServerUrl + "/ifr?url=" + URL.encodeQueryString(source)
          + "&container=wave";
      constructGadgetFromUrl(url);
      return;
    }
    
    GadgetDataStoreImpl.getInstance().getGadgetData(source, waveletName, getInstanceId(),
        new GadgetDataStore.DataCallback() {

      @Override
      public void onError(String message, Throwable t) {
        log("GW.createWidget.DataCallback.onError");
        if ((t != null) && (t.getMessage() != null)) {
          message += " " + t.getMessage();
        }
        showBrokenGadget(message);
      }

      @Override
      public void onDataReady(GadgetMetadata metadata) {
        log("GW.createWidget.DataCallback.onDataReady");
        if (isActive()) {
          ReadableStringSet views = metadata.getViewSet();
          String view;
          if (views.contains(GADGET_PRIMARY_VIEW)) {
            view = GADGET_PRIMARY_VIEW;
          } else if (views.contains(GADGET_DEFAULT_VIEW)) {
            view = GADGET_DEFAULT_VIEW;
          } else if (!views.isEmpty()) {
            view = views.someElement();
          } else {
            showBrokenGadget("Gadget has no view to render.");
            return;
          }
          constructGadgetFromUrl(metadata.getIframeUrl(view));
        }
      }
    });
  }

  /**
   * Sends the gadget state to the wave gadget. Injects the playback state value
   * into the state.
   */
  public void sendGadgetState() {
    if (waveEnabled) {
      if (shouldLog()) {
        log("GW.sendGadgetState: state=", state.toJson());
      }
      GadgetRpcSender.sendGadgetStateRpc(getGadgetName(), state.asJavaScriptObject());
    }
  }

  /**
   * Sends the private gadget state to the wave gadget.
   */
  public void sendPrivateGadgetState() {
    if (waveEnabled) {
      String gadgetId = getGadgetId();
      StateMap privateState = StateMap.createFromStringMap(gadgetId != null ?
          supplement.getGadgetState(gadgetId) : CollectionUtils.<String> emptyMap());
      if (shouldLog()) {        
        log("GW.sendPrivateGadgetState: state=", privateState.toJson());
      }
      GadgetRpcSender.sendPrivateGadgetStateRpc(getGadgetName(), privateState.asJavaScriptObject());
    }
  }

  /**
   * Sends the gadget mode to the wave gadget.
   */
  public void sendMode() {
    if (waveEnabled) {
      StateMap mode = StateMap.create();
      mode.put(PLAYBACK_MODE_KEY, "0");
      mode.put(EDIT_MODE_KEY, editingIndicator.isEditing() ? "1" : "0");
      if (shouldLog()) {        
        log("GW.sendMode: mode=", mode.toJson());
      }
      GadgetRpcSender.sendModeRpc(getGadgetName(), mode.asJavaScriptObject());
    }
  }

  /**
   * Returns the ID of the user who added the gadget as defined in the author
   * attribute. If the attribute is not defined returns the blip author instead
   * (as the best guess for the author for backward compatibility).
   *
   * @return author ID of the user who added the gadget to the wave
   */
  private String getAuthor() {
    String author = element.getAttribute(GadgetConstants.AUTHOR_ATTRIBUTE);
    return (author != null) ? author : blip.getAuthorId().getAddress();
  }

  /**
   * Refreshes the participant information.
   */
  private void refreshParticipantInformation() {
    log("GW.refreshParticipantInformation");
    StringMap<ParticipantId> waveletParticipants = CollectionUtils.createStringMap();
    for (ParticipantId p : blip.getConversation().getParticipantIds()) {
      waveletParticipants.put(p.getAddress(), p);
    }
    for (ParticipantId p : blip.getContributorIds()) {
      waveletParticipants.put(p.getAddress(), p);
    }
    ParticipantId viewerId = new ParticipantId(loginName);
    waveletParticipants.put(viewerId.getAddress(), viewerId);
    List<ParticipantId> participantList = CollectionUtils.newJavaList(waveletParticipants);

    participants = ParticipantInformation.create(
        viewerId.getAddress(), getAuthor(), participantList, getUrlPrefix(), profileManager);

    if (shouldLog()) {
      StringBuilder builder = new StringBuilder();
      builder.append("Participants: ");
      builder.append("I am ").append(participants.getMyId());
      for (ParticipantId participant : participantList) {
        builder.append("; ").append(participant);
      }
      log(builder.toString());
    }  
  }

  /**
   * Refreshes and sends participant information to wave-enabled gadget.
   */
  private void sendCurrentParticipantInformation() {
    if (waveEnabled) {
      refreshParticipantInformation();
      GadgetRpcSender.sendParticipantsRpc(getGadgetName(), participants);
      log("Sent participants: ", participants);
    }
  }

  /**
   * Utility function to perform setPref RPC to the gadget.
   *
   * @param name name of the preference to set.
   * @param value value of the preference.
   */
  public void setGadgetPref(final String name, final String value) {
    log("GW.setGadgetPref.Task is scheduled");
    getTimer().schedule(new Task() {
          
      @Override
      public void execute() {
        log("GW.setGadgetPref.Task is executed");
        if (isActive()) {
          GadgetRpcSender.sendGadgetPrefRpc(getGadgetName(), name, value);
        }
      }
    });
  }

  /**
   * Marks the Widget as inactive after the gadget node is removed from the parent.
   */
  public void setInactive() {
    log("Gadget node removed.");
    supplement.removeListener(supplementedWaveListener);
    active = false;
  }

  private void setPref(String key, String value) {
    if (!canModifyDocument() || (key == null) || (value == null)) {
      return;
    }
    userPrefs.put(key, value);
    if (prefElements.containsKey(key)) {
      if (!prefElements.get(key).getValue().equals(value)) {
        log("Updating preference '", key, "'='", value, "'");
        onModifyingDocument();
        prefElements.get(key).setValue(value);
        blipSubmitter.submit();
      }
    } else {
      log("New preference '", key, "'='", value, "'");
      onModifyingDocument();
      element.getMutableDoc().insertXml(
          Point.end((ContentNode)element), GadgetXmlUtil.constructPrefXml(key, value));
      blipSubmitter.submit();
    }
  }

  /**
   * HACK: This is a workaround for Firefox bug
   * https://bugzilla.mozilla.org/show_bug.cgi?id=498904 Due to this bug the
   * gadget RPCs may be sent to a dead iframe. Changing the iframe ID fixes
   * container-to-gadget communication. Non-wave gadgets may have other issues
   * associated with this bug. But most wave-enabled gadgets should work when
   * the iframe ID is updated in the waveEnable call.
   */
  private void substituteIframeId() {
    log("GW.substituteIframeId");
    
    clientInstanceId = nextClientInstanceId++;
    ui.setIframeId(getGadgetName());
    controllerRegistration(iframeUrl, 0, 0);
  }

  /**
   * Generates a unique gadget ID.
   * TODO(user): Replace with proper MD5-based UUID.
   *
   * @return a unique gadget ID.
   */
  private String generateGadgetId() {
    String name = ModernIdSerialiser.INSTANCE.serialiseWaveletName(waveletName);
    String instanceDescriptor = name + getAuthor() + source;
    String prefix = Integer.toHexString(instanceDescriptor.hashCode());
    String time = Integer.toHexString(new Date().hashCode());
    String version = Long.toHexString(blip.getLastModifiedVersion());
    return prefix + time + version;
  }

  private String generateAndSetGadgetId() {
    if (!canModifyDocument()) {
      return null;
    }
    String id = generateGadgetId();
    element.getMutableDoc().setElementAttribute(element, GadgetConstants.ID_ATTRIBUTE, id);
    return id;
  }

  private String getGadgetId() {
    return element.getAttribute(GadgetConstants.ID_ATTRIBUTE);
  }

  private String getOrGenerateGadgetId() {
    String id = getGadgetId();
    if ((id == null) || id.isEmpty()) {
      id = generateAndSetGadgetId();
    }
    return id;
  }

  private void modifyState(String key, String value) {
    if (!canModifyDocument()) {
      log("Unable to modify state ", key, " ", value);
    } else {
      log("Modifying state ", key, " ", value);
      if (stateElements.containsKey(key)) {
        if (!stateElements.get(key).getValue().equals(value)) {
          onModifyingDocument();
          stateElements.get(key).setValue(value);
        }
      }  else {
        onModifyingDocument();
        element.getMutableDoc().insertXml(
            Point.end((ContentNode)element), GadgetXmlUtil.constructStateXml(key, value));
      }
    }
  }

  private void deleteState(String key) {
    if (!canModifyDocument()) {
      log("Unable to remove state ", key);
    } else {
      log("Removing state ", key);
      if (stateElements.containsKey(key)) {
        onModifyingDocument();
        element.getMutableDoc().deleteNode(stateElements.get(key).getElement());
      }
    }
  }

  Task sendCurrentParticipantInformationTask = new Task() {

    @Override
    public void execute() {
      log("GW.sendCurrentParticipantInformationTask is executed");
      if (isActive()) {
        sendCurrentParticipantInformation();
      }
    }
  };
  
  private void sendWaveGadgetInitialization() {
    log("GW.sendWaveGadgetInitialization");
    sendMode();
    sendCurrentParticipantInformation();
    gadgetStateSubmitter.submitImmediately();
    privateGadgetStateSubmitter.submitImmediately();
    // Send participant information one more time as participant pictures may be
    // loaded with a delay. There is no callback to get the picture update
    // event.
    log("GW.sendCurrentParticipantInformationTask is scheduled, delay=" +
        REPEAT_PARTICIPANT_INFORMATION_SEND_DELAY_MS);
    getTimer().scheduleDelayed(sendCurrentParticipantInformationTask,
        REPEAT_PARTICIPANT_INFORMATION_SEND_DELAY_MS);
  }

  private void updateElementMaps(
      GadgetElementChild child, StringMap<GadgetElementChild> childMap, StateMap stateMap) {
    if (child.getKey() == null) {
      log("Missing key attribute: element ignored.");
      return;
    }

    if (shouldLogFine()) {
      if (childMap.containsKey(child.getKey())) {
        logFine("Old value: ", childMap.get(child.getKey()));
      }
    }  
    childMap.put(child.getKey(), child);
    stateMap.put(child.getKey(), child.getValue());
    if (shouldLogFine()) {
      logFine("Updated element ", child.getKey(), " : ", child.getValue());
    }  
  }

  private void processTitleChild(GadgetElementChild child) {
    titleElement = child;
    String newTitleValue = child.getValue();
    if (newTitleValue == null) {
      newTitleValue = "";
    }
    if (!newTitleValue.equals(ui.getTitleLabelText())) {
      ui.setTitleLabelText(newTitleValue);
    }
  }

  private void removeChildFromMaps(
      GadgetElementChild child, StringMap<GadgetElementChild> childMap, StateMap stateMap) {
    String key = child.getKey();
    if (childMap.containsKey(key)) {
      stateMap.remove(key);
      childMap.remove(key);
      logFine("Removed element ", key);
    }
  }

  private void processChild(GadgetElementChild child) {
    if (child == null) {
      return;
    }
    if (shouldLogFine()) {
      logFine("Processing: ", child);
    }  
    switch (child.getType()) {
      case STATE:
        updateElementMaps(child, stateElements, state);
        break;
      case PREF:
        updateElementMaps(child, prefElements, userPrefs);
        break;
      case TITLE:
        processTitleChild(child);
        break;
      case CATEGORIES:
        logFine("Categories element ignored.");
        break;
      default:
        // Note(user): editor may add/remove selection and cursor nodes.
        if (shouldLogFine()) {
          logFine("Unexpected gadget node ", child.getTag());
        }  
    }
  }

  /**
   * Finds the first copy of the given child in the sibling sequence starting at
   * the given node.
   *
   * @param child Child to find next copy of.
   * @param node Node to scan from.
   * @return Next copy of the child or null if not found.
   */
  private static GadgetElementChild findNextChildCopy(GadgetElementChild child, ContentNode node) {
    if (child == null) {
      return null;
    }
    while (node != null) {
      GadgetElementChild gadgetChild = GadgetElementChild.create(node);
      if (child.isDuplicate(gadgetChild)) {
        return gadgetChild;
      }
      node = node.getNextSibling();
    }
    return null;
  }

  /**
   * Task removes redundant nodes that match redundantNodeCheckChild.
   */
  private final Task removeRedundantNodesTask = new Task() {
    
    @Override
    public void execute() {
      if (!canModifyDocument()) {
        return;
      }
      if (redundantNodeCheckChild != null) {
        GadgetElementChild firstMatchingNode = findNextChildCopy(
            redundantNodeCheckChild, element.getFirstChild());
        GadgetElementChild lastSeenNode = firstMatchingNode;
        while (lastSeenNode != null) {
          lastSeenNode = findNextChildCopy(
              redundantNodeCheckChild, firstMatchingNode.getElement().getNextSibling());
          if (lastSeenNode != null) {
            log("Removing: ", lastSeenNode);
            element.getMutableDoc().deleteNode(lastSeenNode.getElement());
          }
        }
      } else {
        log("Undefined redundant node check child.");
      }
      redundantNodeCheckChild = null;
    }  
  };

  /**
   * Scans nodes and removes duplicate copies of the given child leaving only
   * the first copy.
   * TODO(user): Unit test for node manipulations.
   *
   * @param child Child to delete the duplicates of.
   */
  private void removeRedundantNodes(final GadgetElementChild child) {
    if (!documentModified || (child == null)) {
      return;
    }
    if (redundantNodeCheckChild == null) {
      redundantNodeCheckChild = child;
      log("GW.removeRedundantNodesTask is scheduled");
      getTimer().schedule(removeRedundantNodesTask);
    } else {
      log("Overlapping redundant node check requests.");
    }
  }

  private final ElementChangeTask childAddedTask = new ElementChangeTask() {

    @Override
    void run(ContentNode node) {
      log("GW.childAddedTask.run");
      super.run(node);
    }
    
    @Override
    void processChange(ContentNode node) {
      log("GW.childAddedTask.processChange");
      GadgetElementChild child = GadgetElementChild.create(node);
      if (shouldLog()) {        
        log("Added: ", child);
      }  
      if (child != null) {
        removeRedundantNodes(child);
        processChild(child);
      }
    }
  };

  /**
   * Processes an add child event.
   *
   * @param node the child added to the gadget node.
   */
  public void onChildAdded(ContentNode node) {
    log("GW.onChildAdded");
    childAddedTask.run(node);
  }

  private final ElementChangeTask childRemovedTask = new ElementChangeTask() {

    @Override
    void run(ContentNode node) {
      log("GW.childRemovedTask.run");
      super.run(node);
    }
    
    @Override
    void processChange(ContentNode node) {
      log("GW.childRemovedTask.processChange");      
      GadgetElementChild child = GadgetElementChild.create(node);
      if (shouldLog()) {        
        log("Removed: ", child);
      }  
      switch (child.getType()) {
        case STATE:
          removeChildFromMaps(child, stateElements, state);
          break;
        case PREF:
          removeChildFromMaps(child, prefElements, userPrefs);
          break;
        case TITLE:
          log("Removing title is not supported");
          break;
        case CATEGORIES:
          log("Removing categories is not supported");
          break;
        default:
          // Note(user): editor may add/remove selection and cursor nodes.
          if (shouldLog()) {
            log("Unexpected gadget node removed ", child.getTag());
          }  
      }
    }
  };

  /**
   * Processes a remove child event.
   *
   * @param node
   */
  public void onRemovingChild(ContentNode node) {
    log("GW.onRemovingChild");
    childRemovedTask.run(node);
  }

  /**
   * Rescans all gadget children to update the values stored in the gadget
   * object.
   */
  private void rescanGadgetXmlElements() {
    log("GW.rescanGadgetXmlElements");
    ContentNode childNode = element.getFirstChild();
    while (childNode != null) {
      processChild(GadgetElementChild.create(childNode));
      childNode = childNode.getNextSibling();
    }
  }

  private final ElementChangeTask descendantsMutatedTask = new ElementChangeTask() {

    @Override
    void run(ContentNode node) {
      log("GW.descendantsMutatedTask.run");      
      super.run(node);
    }
    
    @Override
    void processChange(ContentNode node) {
      log("GW.descendantsMutatedTask.processChange");
      rescanGadgetXmlElements();
    }
  };

  private final Task schedulableMutationTask = new Task() {
    
    @Override
    public void execute() {
      log("GW.schedulableMutationTask is executed.");
      descendantsMutatedTask.run(null);
    }
  };

  /**
   * Processes a mutation event.
   */
  public void onDescendantsMutated() {
    log("GW.onDescendantsMutated");
    log("GW.schedulableMutationTask is scheduled.");
    getTimer().schedule(schedulableMutationTask);
  }

  private Object[] expandArgs(Object object, Object ... objects) {
    Object[] args = new Object[objects.length + 1];
    args[0] = object;
    System.arraycopy(objects, 0, args, 1, objects.length);
    return args;
  }

  private static boolean shouldLog() {
    return GadgetLog.shouldLog();
  }
  
  private void log(Object ... objects) {
    if (shouldLog()) {
      GadgetLog.logLazy(expandArgs(clientInstanceLogLabel, objects));
    }
  }

  private static boolean shouldLogFine() {
    return GadgetLog.shouldLogFine();
  }
  
  private void logFine(Object ... objects) {
    if (shouldLogFine()) {
      GadgetLog.logFineLazy(expandArgs(clientInstanceLogLabel, objects));
    }
  }

  /**
   * @return the URL of the client including protocol and host.
   */
  private String getUrlPrefix() {
    return Location.getProtocol() + "//" + Location.getHost();
  }

  private boolean isActive() {
    return active;
  }

  private boolean canModifyDocument() {
    return isActive();
  }

  private static native void excludeCssName() /*-{
    css();
  }-*/;

  private static class BlipEditingIndicator implements EditingIndicator {
    private final ContentElement element;

    /**
     * Constructs editing indicator for the gadget's blip.
     */
    BlipEditingIndicator(ContentElement element) {
      this.element = element;
    }

    /**
     * Returns the current edit state of the blip.
     * TODO(user): add event-driven update of the edit state.
     *
     * @return whether the blip is in edit state.
     */
    @Override
    public boolean isEditing() {
      return (element != null)
          ? AnnotationPainter.isInEditingDocument(ContentElement.ELEMENT_MANAGER, element) : false;
    }
  }

  /**
   * Executes when the document is being modified in response to a user action.
   */
  private void onModifyingDocument() {
    documentModified = true;
    if (toUpdateIframeUrl) {
      scheduleGadgetAttributeUpdate(GadgetConstants.IFRAME_URL_ATTRIBUTE, iframeUrl);
      toUpdateIframeUrl = false;
    }
    if (toUpdateWidth) {
      scheduleGadgetAttributeUpdate(GadgetConstants.LAST_KNOWN_WIDTH_ATTRIBUTE, ui.getIframeWidth());
    }
    if (toUpdateHeight) {
      scheduleGadgetAttributeUpdate(GadgetConstants.LAST_KNOWN_HEIGHT_ATTRIBUTE, ui.getIframeHeight());
    }
  }

  private TimerService getTimer() {
    return SchedulerInstance.getLowPriorityTimer();
  }  
  
  //
  // Tests
  //
  
  /**
   * Creates GadgetWidget instance with preset fields for testing.
   *
   * TODO(user): Refactor to remove test code.
   *
   * @param id client instance ID
   * @param userPrefs user prederences
   * @param waveletName wavelet name
   * @param securityToken security token
   * @param locale locale
   * @return test instance of the widget
   */
  @VisibleForTesting
  static GadgetWidget createForTesting(int id, GadgetUserPrefs userPrefs, WaveletName waveletName,
      Locale locale) {
    GadgetWidget widget = new GadgetWidget();
    widget.clientInstanceId = id;
    widget.userPrefs = userPrefs;
    widget.waveletName = waveletName;
    widget.locale = locale;
    return widget;
  }  
  
  /**
   * @return RPC token for testing
   */
  @VisibleForTesting
  String getRpcToken() {
    return rpcToken;
  }  
}
