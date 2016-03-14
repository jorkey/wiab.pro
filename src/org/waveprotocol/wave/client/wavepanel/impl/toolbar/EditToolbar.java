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

package org.waveprotocol.wave.client.wavepanel.impl.toolbar;

import com.google.common.base.Preconditions;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.http.client.URL;

import org.waveprotocol.wave.client.common.util.KeyComboContext;
import org.waveprotocol.wave.client.common.util.KeyComboManager;
import org.waveprotocol.wave.client.common.util.KeyComboTask;
import org.waveprotocol.wave.client.doodad.attachment.render.ImageThumbnailWrapper;
import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.editor.EditorContext;
import org.waveprotocol.wave.client.editor.EditorContextAdapter;
import org.waveprotocol.wave.client.editor.content.CMutableDocument;
import org.waveprotocol.wave.client.editor.content.ContentElement;
import org.waveprotocol.wave.client.editor.content.ContentNode;
import org.waveprotocol.wave.client.editor.content.FocusedContentRange;
import org.waveprotocol.wave.client.editor.content.misc.StyleAnnotationHandler;
import org.waveprotocol.wave.client.editor.content.paragraph.Paragraph;
import org.waveprotocol.wave.client.editor.content.paragraph.Paragraph.LineStyle;
import org.waveprotocol.wave.client.editor.selection.content.SelectionHelper;
import org.waveprotocol.wave.client.editor.toolbar.ButtonUpdater;
import org.waveprotocol.wave.client.editor.toolbar.ParagraphApplicationController;
import org.waveprotocol.wave.client.editor.toolbar.ParagraphTraversalController;
import org.waveprotocol.wave.client.editor.toolbar.TextSelectionController;
import org.waveprotocol.wave.client.editor.util.EditorAnnotationUtil;
import org.waveprotocol.wave.client.gadget.GadgetXmlUtil;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupView;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.color.ColorHelper;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.gadget.GadgetInfoProviderImpl;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.gadget.GadgetSelectorWidget;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.gadget.GwtGadgetInfoParser;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.i18n.EditToolbarMessages;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;
import org.waveprotocol.wave.client.widget.toolbar.SubmenuToolbarView;
import org.waveprotocol.wave.client.widget.toolbar.ToolbarButtonViewBuilder;
import org.waveprotocol.wave.client.widget.toolbar.ToolbarView;
import org.waveprotocol.wave.client.widget.toolbar.ToplevelToolbarWidget;
import org.waveprotocol.wave.client.widget.toolbar.buttons.ToolbarClickButton;
import org.waveprotocol.wave.client.widget.toolbar.buttons.ToolbarToggleButton;
import org.waveprotocol.wave.media.model.AttachmentIdGenerator;
import org.waveprotocol.wave.media.model.AttachmentIdGeneratorImpl;
import org.waveprotocol.wave.model.document.util.FocusedRange;
import org.waveprotocol.wave.model.document.util.LineContainers;
import org.waveprotocol.wave.model.document.util.Point;
import org.waveprotocol.wave.model.document.util.XmlStringBuilder;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.image.ImageXmlUtil;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.GwtWaverefEncoder;

/**
 * Attaches actions that can be performed in a Wave's "edit mode" to a toolbar.
 * <p>
 * Also constructs an initial set of such actions.
 *
 * @author kalman@google.com (Benjamin Kalman)
 */
public class EditToolbar {

  private final static EditToolbarMessages messages = GWT.create(EditToolbarMessages.class);

  /**
   * Handler for click buttons added with {@link EditToolbar#addClickButton}.
   */
  public interface ClickHandler {
    void onClicked(EditorContext context);
  }

  /**
   * Container for a font family.
   */
  private static final class FontFamily {
    
    public final String description;
    public final String style;
    
    public FontFamily(String description, String style) {
      this.description = description;
      this.style = style;
    }
  }

  /**
   * Attaches editor behaviour to a toolbar, adding all the edit buttons.
   */
  private final EditorToolbarResources.Css css;
  private final ToplevelToolbarWidget toolbarUi;
  private final ParticipantId user;
  private final AttachmentIdGenerator attachmentIdGenerator;

  /** The id of the currently loaded wave. */
  private final WaveId waveId;

  private final EditorContextAdapter editor = new EditorContextAdapter(null);
  private final ButtonUpdater updater = new ButtonUpdater(editor);

  private EditToolbar(EditorToolbarResources.Css css, ToplevelToolbarWidget toolbarUi,
      ParticipantId user, IdGenerator idGenerator, WaveId waveId) {
    this.css = css;
    this.toolbarUi = toolbarUi;
    this.user = user;
    this.waveId = waveId;
    attachmentIdGenerator = new AttachmentIdGeneratorImpl(idGenerator);
  }

  /**
   * Attaches editor behaviour to a toolbar, adding all the edit buttons.
   */
  public static EditToolbar create(ParticipantId user, IdGenerator idGenerator, WaveId waveId) {
    ToplevelToolbarWidget toolbarUi = new ToplevelToolbarWidget();
    EditorToolbarResources.Css css = EditorToolbarResources.Loader.res.css();
    return new EditToolbar(css, toolbarUi, user, idGenerator, waveId);
  }

  /** Constructs the initial set of actions in the toolbar. */
  public void init() {
    ToolbarView group = toolbarUi.addGroup();
    createBoldButton(group);
    createItalicButton(group);
    createUnderlineButton(group);
    createStrikethroughButton(group);

    group = toolbarUi.addGroup();
    createSuperscriptButton(group);
    createSubscriptButton(group);

    group = toolbarUi.addGroup();
    createFontSizeButton(group);
    createFontFamilyButton(group);
    createHeadingButton(group);

    group = toolbarUi.addGroup();
    createFontColorButton(group);
    createFontBackColorButton(group);

    group = toolbarUi.addGroup();
    createIndentButton(group);
    createOutdentButton(group);

    group = toolbarUi.addGroup();
    createUnorderedListButton(group);
    createOrderedListButton(group);

    group = toolbarUi.addGroup();
    createAlignButtons(group);
    createClearFormattingButton(group);

    group = toolbarUi.addGroup();
    createInsertLinkButton(group);
    createRemoveLinkButton(group);

    group = toolbarUi.addGroup();
    createInsertGadgetButton(group, user);

    group = toolbarUi.addGroup();
    createInsertAttachmentButton(group, user);
  }

  private void createBoldButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setIcon(css.bold())
        .setTooltip(messages.boldHint())
        .setShortcut(KeyComboManager.getKeyComboHintByTask(
            KeyComboContext.TEXT_EDITOR, KeyComboTask.FORMAT_BOLD))
        .applyTo(b, createTextSelectionController(b, "fontWeight", "bold", false));
  }

  private void createItalicButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setIcon(css.italic())
        .setTooltip(messages.italicHint())
        .setShortcut(KeyComboManager.getKeyComboHintByTask(
            KeyComboContext.TEXT_EDITOR, KeyComboTask.FORMAT_ITALIC))
        .applyTo(b, createTextSelectionController(b, "fontStyle", "italic", false));
  }

  private void createUnderlineButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setIcon(css.underline())
        .setTooltip(messages.underlineHint())
        .setShortcut(KeyComboManager.getKeyComboHintByTask(
            KeyComboContext.TEXT_EDITOR, KeyComboTask.FORMAT_UNDERLINE))
        .applyTo(b, createTextSelectionController(b, "textDecoration", "underline", false));
  }

  private void createFontBackColorButton(ToolbarView toolbar) {
    final ToolbarClickButton button = toolbar.addClickButton();
    new ToolbarButtonViewBuilder().setIcon(css.backcolor())
        .setTooltip(messages.fontBackcolorHint())
        .applyTo(button, new ToolbarClickButton.Listener() {

      @Override
      public void onClicked() {
        ColorHelper.onSetBackColor(editor, button);
      }
    });
  }

  private void createFontColorButton(ToolbarView toolbar) {
    final ToolbarClickButton button = toolbar.addClickButton();
    new ToolbarButtonViewBuilder().setIcon(css.color())
        .setTooltip(messages.fontColorHint())
        .applyTo(button, new ToolbarClickButton.Listener() {

      @Override
      public void onClicked() {
        ColorHelper.onSetColor(editor, button);
      }
    });
  }

  private void createStrikethroughButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setIcon(css.strikethrough())
        .setTooltip(messages.strikethroughHint())
        .setShortcut(KeyComboManager.getKeyComboHintByTask(
            KeyComboContext.TEXT_EDITOR, KeyComboTask.FORMAT_STRIKETHROUGH))
        .applyTo(b, createTextSelectionController(b, "textDecoration", "line-through", false));
  }

  private void createSuperscriptButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setIcon(css.superscript())
        .setTooltip(messages.superscriptHint())
        .applyTo(b, createTextSelectionController(b, "verticalAlign", "super", false));
  }

  private void createSubscriptButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setIcon(css.subscript())
        .setTooltip(messages.subscriptHint())
        .applyTo(b, createTextSelectionController(b, "verticalAlign", "sub", false));
  }

  private void createFontSizeButton(ToolbarView toolbar) {
    final SubmenuToolbarView submenu = toolbar.addSubmenu();
    new ToolbarButtonViewBuilder().setIcon(css.fontSize())
        .setTooltip(messages.fontSizeHint())
        .applyTo(submenu, null);
    submenu.setShowDropdownArrow(false); // Icon already has dropdown arrow.
    // TODO(kalman): default text size option.
    ToolbarView group = submenu.addGroup();
    for (int size : asArray(8, 9, 10, 11, 12, 14, 16, 18, 21, 24, 28, 32, 36, 42, 48, 56, 64, 72)) {
      ToolbarToggleButton b = group.addToggleButton();
      double baseSize = 12.0;
      b.setVisualElement(createFontSizeElement(size));
      b.addListener(createTextSelectionController(b, "fontSize",
          Math.round(size / baseSize * 100.0) / 100.0 + "em", false));
    }
  }

  private Element createFontSizeElement(int size) {
    Element e = Document.get().createSpanElement();
    e.setInnerText(String.valueOf(size));
    return e;
  }

  private void createFontFamilyButton(ToolbarView toolbar) {
    SubmenuToolbarView submenu = toolbar.addSubmenu();
    new ToolbarButtonViewBuilder().setIcon(css.fontFamily())
        .setTooltip(messages.fontFamilyHint())
        .applyTo(submenu, null);
    submenu.setShowDropdownArrow(false); // Icon already has dropdown arrow.
    createFontFamilyGroup(submenu.addGroup(), new FontFamily("Default", null));
    createFontFamilyGroup(submenu.addGroup(),
        new FontFamily("Sans Serif", "sans-serif"),
        new FontFamily("Serif", "serif"),
        new FontFamily("Wide", "arial black,sans-serif"),
        new FontFamily("Narrow", "arial narrow,sans-serif"),
        new FontFamily("Fixed Width", "monospace"));
    createFontFamilyGroup(submenu.addGroup(),
        new FontFamily("Arial", "arial,helvetica,sans-serif"),
        new FontFamily("Comic Sans MS", "comic sans ms,sans-serif"),
        new FontFamily("Courier New", "courier new,monospace"),
        new FontFamily("Garamond", "garamond,serif"),
        new FontFamily("Georgia", "georgia,serif"),
        new FontFamily("Tahoma", "tahoma,sans-serif"),
        new FontFamily("Times New Roman", "times new roman,serif"),
        new FontFamily("Trebuchet MS", "trebuchet ms,sans-serif"),
        new FontFamily("Verdana", "verdana,sans-serif"));
  }

  private void createFontFamilyGroup(ToolbarView toolbar, FontFamily... families) {
    for (FontFamily family : families) {
      ToolbarToggleButton b = toolbar.addToggleButton();
      b.setVisualElement(createFontFamilyElement(family));
      b.addListener(createTextSelectionController(b, "fontFamily", family.style, false));
    }
  }

  private Element createFontFamilyElement(FontFamily family) {
    Element e = Document.get().createSpanElement();
    e.getStyle().setProperty("fontFamily", family.style);
    e.setInnerText(family.description);
    return e;
  }

  private void createClearFormattingButton(ToolbarView toolbar) {
    new ToolbarButtonViewBuilder().setIcon(css.clearFormatting())
        .setTooltip(messages.clearFormattingHint())
        .applyTo(toolbar.addClickButton(), new ToolbarClickButton.Listener() {

      @Override
      public void onClicked() {
        EditorAnnotationUtil.clearTextStyleAnnotationsOverSelection(editor);
        createClearHeadingsListener().onClicked();
      }
    });
  }

  private void createInsertGadgetButton(ToolbarView toolbar, final ParticipantId user) {
    new ToolbarButtonViewBuilder()
        .setIcon(css.insertGadget())
        .setTooltip(messages.insertGadgetHint())
        .applyTo(toolbar.addClickButton(), new ToolbarClickButton.Listener() {
          
      @Override
      public void onClicked() {
        final SelectionHelper selectionHelper = editor.getSelectionHelper();
        final FocusedRange oldFocusedRange = selectionHelper.getSelectionRange();
        GadgetSelectorWidget selector = new GadgetSelectorWidget(new GadgetInfoProviderImpl(
            new GwtGadgetInfoParser()));
        selector.addFeaturedOptions();
        final UniversalPopup popup = selector.showInPopup();
        selector.setListener(new GadgetSelectorWidget.Listener() {

          @Override
          public void onSelect(String url) {
            // Restore selection before gadget insertion.
            if (selectionHelper.getSelectionRange() == null) {
              selectionHelper.setSelectionRange(oldFocusedRange);
            }  
            
            insertGadget(url);
            popup.hide();
          }

          @Override
          public void onHide() {
            // Restore selection before hiding of popup.
            if (selectionHelper.getSelectionRange() == null) {
              selectionHelper.setSelectionRange(oldFocusedRange);
            }  
          }
        });
      }
    });
  }

  private void insertGadget(String url) {
    int from = -1;
    FocusedRange focusedRange = editor.getSelectionHelper().getSelectionRange();
    if (focusedRange != null) {
      from = focusedRange.getFocus();
    }
    if (url != null && !url.isEmpty()) {
      XmlStringBuilder xml = GadgetXmlUtil.constructXml(url, "", user.getAddress());
      CMutableDocument document = editor.getDocument();
      if (document == null) {
        return;
      }
      if (from != -1) {
        Point<ContentNode> point = document.locate(from);
        document.insertXml(point, xml);
      } else {
        LineContainers.appendLine(document, xml);
      }
    }
  }

  private void createAlignButton(
      ToolbarView toolBar, Paragraph.Alignment alignment, String icon,
      String tooltip, String shortcut) {
    ToolbarToggleButton b = toolBar.addToggleButton();
    new ToolbarButtonViewBuilder().setIcon(icon)
        .setTooltip(tooltip).setShortcut(shortcut)
        .applyTo(b, createParagraphApplicationController(b, alignment));
  }

  private void createInsertAttachmentButton(ToolbarView toolbar, final ParticipantId user) {
    WaveRef waveRef = WaveRef.of(waveId);
    Preconditions.checkState(waveRef != null);
    final String waveRefToken = URL.encode(GwtWaverefEncoder.encodeToUriQueryString(waveRef));

    new ToolbarButtonViewBuilder().setIcon(css.insertAttachment())
        .setTooltip(messages.insertAttachmentHint())
        .applyTo(toolbar.addClickButton(), new ToolbarClickButton.Listener() {

      @Override
      public void onClicked() {
        AttachmentPopupView attachmentView = new AttachmentPopupWidget();
        attachmentView.init(new AttachmentPopupView.Listener.Impl() {

          @Override
          public void onDone(String encodedWaveRef, String attachmentId, String fullFileName) {
            /**
             * From UploadToolbarAction in Walkaround
             * @author hearnden@google.com (David Hearnden)
             */
            CMutableDocument doc = editor.getDocument();                
            Point<ContentNode> point = getValidPoint(doc, getSelectionPoint());
            XmlStringBuilder content = ImageXmlUtil.constructXml(attachmentId,
                extractAttachmentFileName(fullFileName));
            ImageThumbnailWrapper thumbnail = ImageThumbnailWrapper.of(
                doc.insertXml(point, content));
            thumbnail.setAttachmentId(attachmentId);
          }
          
          /** Extracts a file name linking to the attachment URL. */
          private String extractAttachmentFileName(String fullFileName) {
            int lastSlashPos = fullFileName.lastIndexOf("/");
            int lastBackSlashPos = fullFileName.lastIndexOf("\\");
            if (lastSlashPos != -1) {
              return fullFileName.substring(lastSlashPos + 1, fullFileName.length());
            }
            if (lastBackSlashPos != -1) {
              return fullFileName.substring(lastBackSlashPos + 1, fullFileName.length());
            }
            return fullFileName;
          }

          // Walkaround for situation when point's container element is filtered out
          // by this document => point is treated as invalid.          
          private Point<ContentNode> getValidPoint(CMutableDocument doc,
              Point<ContentNode> srcPoint) {
            if (srcPoint != null) {
              if (!srcPoint.isInTextNode()) {
                ContentNode nodeAfter = srcPoint.getNodeAfter();
                if (nodeAfter != null) {
                  try {
                    Point.checkRelationship(doc, doc.asElement(srcPoint.getContainer()),
                        nodeAfter, "");
                  } catch (IllegalArgumentException e) {
                    return Point.before(doc, nodeAfter);
                  }
                }  
              }
            }
            return srcPoint;
          }
        });

        attachmentView.setAttachmentId(attachmentIdGenerator.newAttachmentId());
        attachmentView.setWaveRef(waveRefToken);
        attachmentView.show();
      }
    });
  }

  private void createInsertLinkButton(ToolbarView toolbar) {
    // TODO (Yuri Z.) use createTextSelectionController when the full
    // link doodad is incorporated
    new ToolbarButtonViewBuilder().setIcon(css.insertLink())
        .setTooltip(messages.insertLinkHint())
        .setShortcut(KeyComboManager.getKeyComboHintByTask(
            KeyComboContext.TEXT_EDITOR, KeyComboTask.CREATE_LINK))
        .applyTo(toolbar.addClickButton(), new ToolbarClickButton.Listener() {

      @Override
      public void onClicked() {
        LinkerHelper.onCreateLink(editor);
      }
    });
  }

  private void createRemoveLinkButton(ToolbarView toolbar) {
    new ToolbarButtonViewBuilder().setIcon(css.removeLink())
        .setTooltip(messages.removeLinkHint())
        .setShortcut(KeyComboManager.getKeyComboHintByTask(
            KeyComboContext.TEXT_EDITOR, KeyComboTask.CLEAR_LINK))
        .applyTo(toolbar.addClickButton(), new ToolbarClickButton.Listener() {

      @Override
      public void onClicked() {
        LinkerHelper.onClearLink(editor);
      }
    });
  }

  private ToolbarClickButton.Listener createClearHeadingsListener() {
    return new ParagraphTraversalController(editor, new ContentElement.Action() {
      
        @Override
        public void execute(ContentElement e) {
          e.getMutableDoc().setElementAttribute(e, Paragraph.SUBTYPE_ATTR, null);
        }
      });
  }

  private void createHeadingButton(ToolbarView toolbar) {
    SubmenuToolbarView submenu = toolbar.addSubmenu();
    new ToolbarButtonViewBuilder().setIcon(css.heading())
        .setTooltip(messages.headingHint())
        .applyTo(submenu, null);
    submenu.setShowDropdownArrow(false); // Icon already has dropdown arrow.
    ToolbarClickButton defaultButton = submenu.addClickButton();
    new ToolbarButtonViewBuilder()
        .setText("Default")
        .applyTo(defaultButton, createClearHeadingsListener());
    ToolbarView group = submenu.addGroup();
    for (int level : asArray(1, 2, 3, 4)) {
      ToolbarToggleButton b = group.addToggleButton();
      b.setVisualElement(createHeadingElement(level));
      b.addListener(createParagraphApplicationController(b, Paragraph.regularStyle("h" + level)));
    }
  }

  private Element createHeadingElement(int level) {
    Element e = Document.get().createElement("h" + level);
    e.getStyle().setMarginTop(2, Unit.PX);
    e.getStyle().setMarginBottom(2, Unit.PX);
    e.setInnerText("Heading " + level);
    return e;
  }

  private void createIndentButton(ToolbarView toolbar) {
    ToolbarClickButton b = toolbar.addClickButton();
    new ToolbarButtonViewBuilder().setIcon(css.indent())
        .setTooltip(messages.indentHint())
        .applyTo(b, new ParagraphTraversalController(editor, Paragraph.INDENTER));
  }

  private void createOutdentButton(ToolbarView toolbar) {
    ToolbarClickButton b = toolbar.addClickButton();
    new ToolbarButtonViewBuilder().setIcon(css.outdent())
        .setTooltip(messages.outdentHint())
        .applyTo(b, new ParagraphTraversalController(
        editor, Paragraph.OUTDENTER));
  }

  private void createUnorderedListButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder().setIcon(css.unorderedlist())
        .setTooltip(messages.unorderedListHint())
        .applyTo(b, createParagraphApplicationController(
        b, Paragraph.listStyle(null)));
  }

  private void createOrderedListButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder().setIcon(css.orderedlist())
        .setTooltip(messages.orderedListHint())
        .applyTo(b, createParagraphApplicationController(
            b, Paragraph.listStyle(Paragraph.LIST_STYLE_DECIMAL)));
  }

  private void createAlignButtons(ToolbarView group) {
    createAlignButton(group, Paragraph.Alignment.LEFT, css.alignLeft(),
        messages.alignLeftHint(), null);
    createAlignButton(group, Paragraph.Alignment.CENTER, css.alignCentre(),
        messages.alignCenterHint(), null);
    createAlignButton(group, Paragraph.Alignment.RIGHT, css.alignRight(),
        messages.alignRightHint(), null);
  }

  /**
   * Adds a button to this toolbar.
   */
  public void addClickButton(String icon, final ClickHandler handler) {
    ToolbarClickButton.Listener uiHandler = new ToolbarClickButton.Listener() {
      
      @Override
      public void onClicked() {
        handler.onClicked(editor);
      }
    };
    new ToolbarButtonViewBuilder().setIcon(icon).applyTo(toolbarUi.addClickButton(), uiHandler);
  }

  /**
   * Starts listening to editor changes.
   *
   * @throws IllegalStateException if this toolbar is already enabled
   * @throws IllegalArgumentException if the editor is <code>null</code>
   */
  public void enable(Editor editor) {
    this.editor.checkEditor(null);
    Preconditions.checkArgument(editor != null);
    this.editor.switchEditor(editor);
    editor.addUpdateListener(updater);
    updater.updateButtonStates();
  }

  /**
   * Stops listening to editor changes.
   *
   * @throws IllegalStateException if this toolbar is not currently enabled
   * @throws IllegalArgumentException if this toolbar is currently enabled for a
   *         different editor
   */
  public void disable(Editor editor) {
    this.editor.checkEditor(editor);
    // The above won't throw if we're not currently enabled, but it makes sure
    // 'editor' is the same as the current editor, if any. So if 'editor' is
    // null, it means we aren't enabled (the wrapped editor is null too).
    Preconditions.checkState(editor != null);
    editor.removeUpdateListener(updater);
    this.editor.switchEditor(null);
  }

  /**
   * @return the {@link ToplevelToolbarWidget} backing this toolbar.
   */
  public ToplevelToolbarWidget getWidget() {
    return toolbarUi;
  }

  private ToolbarToggleButton.Listener createParagraphApplicationController(ToolbarToggleButton b,
      LineStyle style) {
    return updater.add(new ParagraphApplicationController(b, editor, style));
  }

  private ToolbarToggleButton.Listener createTextSelectionController(ToolbarToggleButton b,
      String styleName, String value, boolean wholeParagraph) {
    return updater.add(new TextSelectionController(b, editor,
        StyleAnnotationHandler.key(styleName), value, wholeParagraph));
  }

  @SuppressWarnings("unchecked")
  private static <E> E[] asArray(E... elements) {
    return elements;
  }
  
  private Point<ContentNode> getSelectionPoint() {
    Point<ContentNode> point;
    FocusedContentRange selection = editor.getSelectionHelper().getSelectionPoints();
    if (selection != null) {
      point = selection.getFocus();
    } else {
      // Focus was probably lost.  Bring it back.
      editor.focus(false);
      selection = editor.getSelectionHelper().getSelectionPoints();
      if (selection != null) {
        point = selection.getFocus();
      } else {
        // Still no selection.  Oh well, put it at the end.
        CMutableDocument doc = editor.getDocument();            
        point = doc.locate(doc.size() - 1);
      }
    }
    return point;
  }
}
