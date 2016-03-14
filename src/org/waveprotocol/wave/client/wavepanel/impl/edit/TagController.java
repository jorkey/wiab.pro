/**
 *
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

package org.waveprotocol.wave.client.wavepanel.impl.edit;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.user.client.Command;

import org.waveprotocol.box.webclient.client.WebClient;
import org.waveprotocol.wave.client.common.util.KeyCombo;
import org.waveprotocol.wave.client.common.util.KeyComboContext;
import org.waveprotocol.wave.client.common.util.KeyComboManager;
import org.waveprotocol.wave.client.common.util.KeyComboTask;
import org.waveprotocol.wave.client.scheduler.Scheduler.Task;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.client.wave.LocalSupplementedWave;
import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.event.EventHandlerRegistry;
import org.waveprotocol.wave.client.wavepanel.event.KeySignalHandler;
import org.waveprotocol.wave.client.wavepanel.event.MouseComeLeaveEvent;
import org.waveprotocol.wave.client.wavepanel.event.WaveClickHandler;
import org.waveprotocol.wave.client.wavepanel.event.WaveMouseComeLeaveHandler;
import org.waveprotocol.wave.client.wavepanel.event.WaveMouseDownHandler;
import org.waveprotocol.wave.client.wavepanel.event.WaveMouseUpHandler;
import org.waveprotocol.wave.client.wavepanel.impl.edit.i18n.TagMessages;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicTagView.TagState;
import org.waveprotocol.wave.client.wavepanel.view.TagView;
import org.waveprotocol.wave.client.wavepanel.view.TagsView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TagsViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TypeCodes;
import org.waveprotocol.wave.client.widget.dialog.DialogBox;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.util.Pair;

/**
 * Installs the add/remove tag controls.
 *
 */
public final class TagController {

  /**
   * Builds and installs the tag control feature.
   */
  public static void install(WavePanel panel, ModelAsViewProvider models,
      SupplementedWave supplement, WaveletId waveletId, TagsViewBuilder.Css css,
      TagMessages messages) {
    TagController tagController = new TagController(panel.getViewProvider(), models, supplement,
        waveletId, css, messages);
    tagController.install(panel);
  }

  private final DomAsViewProvider views;
  private final ModelAsViewProvider models;
  private final SupplementedWave supplement;
  private final WaveletId waveletId;
  private final TagsViewBuilder.Css css;
  private final TagMessages messages;

  private boolean buttonDown = false;

  /**
   * @param localDomain nullable. if provided, automatic suffixing will occur.
   */
  private TagController(DomAsViewProvider views, ModelAsViewProvider models,
      SupplementedWave supplement, WaveletId waveletId, TagsViewBuilder.Css css,
      TagMessages messages) {
    this.views = views;
    this.models = models;
    this.supplement = supplement;
    this.waveletId = waveletId;
    this.css = css;
    this.messages = messages;
  }

  private void install(WavePanel panel) {
    EventHandlerRegistry handlers = panel.getHandlers();
    handlers.registerClickHandler(TypeCodes.kind(Type.ADD_TAG), new WaveClickHandler() {

      @Override
      public boolean onClick(ClickEvent event, Element context) {
        handleAddButtonClicked(context);
        return true;
      }
    });
    handlers.registerMouseDownHandler(TypeCodes.kind(Type.ADD_TAG), new WaveMouseDownHandler() {

      @Override
      public boolean onMouseDown(MouseDownEvent event, Element context) {
        buttonDown = true;
        checkButtonView();
        return false;
      }
    });
    handlers.registerMouseUpHandler(TypeCodes.kind(Type.ADD_TAG), new WaveMouseUpHandler() {

      @Override
      public boolean onMouseUp(MouseUpEvent event, Element context) {
        buttonDown = false;
        checkButtonView();
        return false;
      }
    });
    handlers.registerMouseComeLeaveHandler(TypeCodes.kind(Type.ADD_TAG),
        new WaveMouseComeLeaveHandler() {

      @Override
      public boolean onMouseComeLeave(MouseComeLeaveEvent event, Element context) {
        buttonDown = false;
        checkButtonView();
        return false;
      }
    });
    handlers.registerClickHandler(TypeCodes.kind(Type.TAG), new WaveClickHandler() {

      @Override
      public boolean onClick(ClickEvent event, Element context) {
        handleTagClicked(context);
        return true;
      }
    });
    panel.getKeyRouter().register(KeyComboManager.getKeyCombosByTask(
        KeyComboContext.WAVE, KeyComboTask.ADD_TAG), new KeySignalHandler() {

      @Override
      public boolean onKeySignal(KeyCombo key) {
        final Element addButton = Document.get().getElementById(
            WebClient.ADD_TAG_BUTTON_ID);
        addTags(addButton);
        return false;
      }
    });
  }

  /**
   * Shows an add-tag popup.
   */
  private void handleAddButtonClicked(Element addButton) {
    addTags(addButton);
  }

  private void handleTagClicked(Element context) {
    TagView tagView = views.asTag(context);
    if (!TagState.REMOVED.equals(tagView.getState()) ) {
      final Pair<Conversation, String> tag = models.getTag(tagView);
      DialogBox.confirm(messages.removeTagPrompt(tag.second), new Command() {

        @Override
        public void execute() {
          tag.first.removeTag(tag.second);
          markAsRead();
        }
      });
    }
  }

  private void addTags(final Element addButton) {
    DialogBox.prompt(messages.addTagPrompt(), "", new DialogBox.InputProcessor() {

      @Override
      public void process(String input) {
        if (input != null) {
          addTagsInner(addButton, input);
        }
      }
    });
  }

  private void checkButtonView() {
    Element addButton = Document.get().getElementById(WebClient.ADD_TAG_BUTTON_ID);
    addButton.setClassName(buttonDown ? css.addButtonPressed() : css.addButton());
  }

  private void markAsRead() {
    SchedulerInstance.getLowPriorityTimer().schedule(new Task() {

      @Override
      public void execute() {
        supplement.markTagsAsRead(waveletId);
      }
    });
  }

  private void addTagsInner(Element addButton, String input) {
    String[] tags = input.split(",");
    TagsView tagUi = views.tagsFromAddButton(addButton);
    Conversation conversation = models.getTags(tagUi);
    for (String tag : tags) {
      tag = tag.trim();
      if (!tag.isEmpty()) {
        conversation.addTag(tag);
      }
    }
    markAsRead();
  }
}
