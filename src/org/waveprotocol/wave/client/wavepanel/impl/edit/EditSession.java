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

package org.waveprotocol.wave.client.wavepanel.impl.edit;

import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Widget;

import org.waveprotocol.wave.client.common.util.EventWrapper;
import org.waveprotocol.wave.client.common.util.KeyCombo;
import org.waveprotocol.wave.client.common.util.KeyComboContext;
import org.waveprotocol.wave.client.common.util.KeyComboTask;
import org.waveprotocol.wave.client.common.util.KeySignalListener;
import org.waveprotocol.wave.client.common.util.LogicalPanel;
import org.waveprotocol.wave.client.common.util.SignalEvent;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.debug.logger.LogLevel;
import org.waveprotocol.wave.client.doodad.selection.SelectionExtractor;
import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.editor.EditorAction;
import org.waveprotocol.wave.client.editor.EditorContext;
import org.waveprotocol.wave.client.editor.EditorSettings;
import org.waveprotocol.wave.client.editor.Editors;
import org.waveprotocol.wave.client.editor.content.ContentDocument;
import org.waveprotocol.wave.client.editor.keys.KeyBindingRegistry;
import org.waveprotocol.box.webclient.flags.Flags;
import org.waveprotocol.wave.client.wave.InteractiveDocument;
import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.impl.WavePanelImpl;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.LinkerHelper;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.focus.FocusFramePresenter;
import org.waveprotocol.wave.model.conversation.focus.ObservableFocusFramePresenter;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.util.Preconditions;

/**
 * Interprets focus-frame movement as reading actions, and also provides an
 * ordering for focus frame movement, based on unread content.
 */
public final class EditSession implements WavePanel.LifecycleListener, KeySignalListener,
    FocusFramePresenter.FocusMoveValidator {

  /**
   * Edit session listener.
   */
  public interface Listener {
    
    /**
     * Called when edit session is started.
     * 
     * @param editor the editor
     * @param blip the blip
     */
    void onSessionStart(Editor editor, ConversationBlip blip);

    /**
     * Called when edit session is ended.
     * 
     * @param editor the editor
     * @param blip the blip
     */    
    void onSessionEnd(Editor editor, ConversationBlip blip);
  }

  /**
   * Finisher of the edit session.
   */
  public interface Finisher {
    
    void onEndEditing(Command onDone);
    void onFocusMove(Command onDone);
    void onWaveCompletion(Command onDone);
    boolean isExitAllowed();
    boolean shouldDraftBeSaved();
  }

  public class QuietFinisher implements Finisher {
    
    private final EditSession session;

    private void saveChanges(Command onDone) {
      if (session.isDraftModified())
        session.leaveDraftMode(true);

      onDone.execute();
    }

    public QuietFinisher(EditSession session) {
      this.session = session;
    }

    @Override
    public void onEndEditing(Command onDone) {
      saveChanges(onDone);
    }

    @Override
    public void onFocusMove(Command onDone) {
      saveChanges(onDone);
    }

    @Override
    public void onWaveCompletion(Command onDone) {
      saveChanges(onDone);
    }

    @Override
    public boolean isExitAllowed() {
      return true;
    }

    @Override
    public boolean shouldDraftBeSaved() {
      return true;
    }
  }

  private static final EditorSettings EDITOR_SETTINGS = new EditorSettings()
      .setHasDebugDialog(LogLevel.showErrors() || Flags.get().enableEditorDebugging())
      .setUndoEnabled(Flags.get().enableUndo())
      .setUseFancyCursorBias(Flags.get().useFancyCursorBias())
      .setUseSemanticCopyPaste(Flags.get().useSemanticCopyPaste())
      .setUseWhitelistInEditor(Flags.get().useWhitelistInEditor())
      .setUseWebkitCompositionEvents(Flags.get().useWebkitCompositionEvents())
      .setCloseSuggestionsMenuDelayMs(Flags.get().closeSuggestionsMenuDelayMs());

  private static final KeyBindingRegistry KEY_BINDINGS = new KeyBindingRegistry();

  private final LogicalPanel container;
  private final CopyOnWriteSet<Listener> listeners = CopyOnWriteSet.create();
  
  /** Writes caret annotations based on selection. */
  // This is only a dependency, rather than being a listener of EditSession
  // events, in order that the extractor can get the session-end event before
  // the editor has been detached.
  private final SelectionExtractor selectionExtractor;
  /** The UI of the document being edited. */
  private ConversationBlip editing;

  /** Editor control. */
  private Editor editor;

  private Actions actions;

  private Finisher finisher;

  private final ObservableFocusFramePresenter focus;

  static LoggerBundle LOG = new DomLogger("client");

  EditSession(final ObservableFocusFramePresenter focus, LogicalPanel container,
      SelectionExtractor selectionExtractor) {
    this.focus = focus;
    this.container = container;
    this.selectionExtractor = selectionExtractor;
    this.finisher = new QuietFinisher(this);
    
    focus.addListener(new ObservableFocusFramePresenter.Listener() {

      @Override
      public void onFocusOut(ConversationBlip oldFocused) {
        endSession();        
      }

      @Override
      public void onFocusIn(ConversationBlip newFocused) {}
    });
  }

  public static EditSession create(ObservableFocusFramePresenter focus, WavePanel panel,
      SelectionExtractor selectionExtractor) {
    EditSession edit = new EditSession(focus, panel.getGwtPanel(), selectionExtractor);
    if (panel.hasContents()) {
      edit.onInit();
    }

    // Warms up the editor code (e.g., internal statics) by creating and throwing
    // away an editor, in order to reduce the latency of starting the first edit
    // session.
    Editors.create();

    edit.setFinisher(new InteractiveEditSessionFinisher());    
    
    return edit;
  }

  public void setActions(Actions actions) {
    this.actions = actions;
  }

  @Override
  public void onInit() {
  }

  @Override
  public void onReset() {
    endSession();
  }

  /**
   * Starts an edit session on a blip. If there is already an edit session on
   * another blip, that session will be moved to the new blip.
   *
   * @param blip blip to edit
   */
  public void startEditing(ConversationBlip blip) {
    endSession();
    startNewSession(blip);
  }

  /**
   * Ends the current edit session, if there is one.
   */
  public void stopEditing() {
    if (isDraftMode())
      leaveDraftMode(true);

    endSession();
  }

  void finishEditing(final boolean endWithDone) {
    if (isDraftModified() && !endWithDone) {
      finisher.onEndEditing(new Command() {

        @Override
        public void execute() {
          if (finisher.isExitAllowed()) {
            leaveDraftMode(finisher.shouldDraftBeSaved());

            //if edited blip is empty => delete it
            ConversationBlip edited = editing;
            endSession();
            actions.deleteBlip(edited, Actions.DeleteOption.DELETE_EMPTY_ONLY);
          }
        }
      });
    } else {
      stopEditing();
    }  
  }

  /**
   * Starts a new document-edit session on a blip.
   *
   * @param blipUi blip to edit.
   */
  private void startNewSession(ConversationBlip blip) {
    Preconditions.checkArgument(!isEditing(), "Editing is already on!");

    // Find the document.
    InteractiveDocument content = blip.getContent();
    ContentDocument document = content.getDocument();

    // Create or re-use and editor for it.
    editor = Editors.attachTo(document, blip);
    container.doAdopt(editor.getWidget());
    editor.init(null, KEY_BINDINGS, EDITOR_SETTINGS);
    editor.addKeySignalListener(this);
    KEY_BINDINGS.registerTask(KeyComboContext.TEXT_EDITOR, KeyComboTask.CREATE_LINK,
        new EditorAction() {
          
      @Override
      public void execute(EditorContext context) {
        LinkerHelper.onCreateLink(context);
      }
    });
    
    KEY_BINDINGS.registerTask(KeyComboContext.TEXT_EDITOR, KeyComboTask.CLEAR_LINK,
        new EditorAction() {
          
      @Override
      public void execute(EditorContext context) {
        LinkerHelper.onClearLink(context);
      }
    });
    
    editor.setEditing(true);
    focus.setEditing(true);
    editor.focus(false);
    editing = blip;
    selectionExtractor.start(editor);
    
    content.startDiffSuppression(false);
    
    fireOnSessionStart(editor, blip);
  }

  /**
   * Stops editing if there is currently an edit session.
   */
  private void endSession() {
    if (!isEditing()) {
      return;
    }
    
    selectionExtractor.stop(editor);
    container.doOrphan(editor.getWidget());
    editor.blur();
    editor.setEditing(false);
    focus.setEditing(false);
    // "removeContent" just means detach the editor from the document.
    editor.removeContent();
    editor.reset();
    
    InteractiveDocument content = editing.getContent();
    content.stopDiffSuppression();
    
    // TODO(user): this does not work if the view has been deleted and
    // detached.
    Editor oldEditor = editor;
    ConversationBlip oldEditing = editing;
    editor = null;
    editing = null;

    fireOnSessionEnd(oldEditor, oldEditing);
  }

  /** @return true if there is an active edit session. */
  public boolean isEditing() {
    return editing != null;
  }

  public boolean isDraftMode() {
    return isEditing() && editor.isDraftMode();
  }

  public boolean isDraftModified() {
    return isEditing() && isDraftMode() && editor.isDraftModified();
  }

  /** @return the blip UI of the current edit session, or {@code null}. */
  public ConversationBlip getBlip() {
    return editing;
  }

  /** @return the editor of the current edit session, or {@code null}. */
  public Editor getEditor() {
    return editor;
  }

  //
  // Events.
  //

  @Override
  public boolean onKeySignal(Widget sender, SignalEvent signal) {
    KeyCombo key = EventWrapper.getKeyCombo(signal);
    switch (key.getAssignedTask(KeyComboContext.TEXT_EDITOR)) {
      case DONE_WITH_EDITING:
        finishEditing(true);
        return true;
      case CANCEL_EDITING:
        finishEditing(false);
        return true;
    }
    return false;
  }

  //
  // FocusMoveValidator
  //

  @Override
  public boolean canMoveFocus(ConversationBlip oldBlip, final ConversationBlip newBlip) {
    if (isDraftModified()) {
      finisher.onFocusMove(new Command() {

        @Override
        public void execute() {
          if (finisher.isExitAllowed()) {
            leaveDraftMode(finisher.shouldDraftBeSaved());
            endSession();
            focus.focus(newBlip);
          }
        }
      });
      return false;
    }
    return true;    
  }
  
  //
  // Listeners.
  //

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  private void fireOnSessionStart(Editor editor, ConversationBlip blip) {
    for (Listener listener : listeners) {
      listener.onSessionStart(editor, blip);
    }
  }

  private void fireOnSessionEnd(Editor editor, ConversationBlip blip) {
    for (Listener listener : listeners) {
      listener.onSessionEnd(editor, blip);
    }
  }

  public void enterDraftMode() {
    if (isEditing()) {
      editor.enterDraftMode();
    }  
  }

  public void leaveDraftMode(boolean saveChanges) {
    if (isEditing()) {
      editor.leaveDraftMode(saveChanges);
    }  
  }

  public void setFinisher(Finisher finisher) {
    this.finisher = finisher;
  }

  public Finisher getFinisher() {
    return finisher;
  }
}
