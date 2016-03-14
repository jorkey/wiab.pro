/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.wave.client.common.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manager of key combinations linking them with tasks.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class KeyComboManager {

  private static class Context {

    private Map<KeyCombo, KeyComboTask> keyComboToTask = new HashMap<>();

    public void put(KeyComboTask task, KeyCombo... keyCombos) {
      for (KeyCombo keyCombo : keyCombos) {
        assert keyComboToTask.get(keyCombo) == null :
            "The keyCombo is already used: " + keyCombo.getHint();
        keyComboToTask.put(keyCombo, task);
      }
    }

    public KeyComboTask getTaskByKeyCombo(KeyCombo keyCombo) {
      KeyComboTask task = keyComboToTask.get(keyCombo);
      if (task == null) {
        task = KeyComboTask.UNKNOWN;
      }
      return task;
    }

    public Set<KeyCombo> getKeyCombosByTask(KeyComboTask task) {
      Set<KeyCombo> keyCombos = new HashSet<>();
      for (Map.Entry<KeyCombo, KeyComboTask> entry : keyComboToTask.entrySet()) {
        if (task.equals(entry.getValue()) ) {
          keyCombos.add(entry.getKey());
        }
      }
      return keyCombos;
    }

    public String getKeyComboHintByTask(KeyComboTask task) {
      String s = "";
      boolean isNotFirst = false;
      for (KeyCombo keyCombo : getKeyCombosByTask(task)) {
        if (isNotFirst) {
          s += ", ";
        }
        s += keyCombo.getHint();
        isNotFirst = true;
      }
      return s;
    }

    public String getFirstKeyComboHintByTask(KeyComboTask task) {
      for (KeyCombo keyCombo : getKeyCombosByTask(task)) {
        return keyCombo.getHint();
      }
      return "";
    }
  }

  private static final Map<KeyComboContext, Context> contexts = new HashMap<>();

  static {
    final boolean isMac = UserAgent.isMac();

    // Text editor
    contexts.put(KeyComboContext.TEXT_EDITOR, new Context() {
      {
        put(KeyComboTask.FORMAT_BOLD, isMac ? KeyCombo.META_B : KeyCombo.CTRL_B);
        put(KeyComboTask.FORMAT_ITALIC, isMac ? KeyCombo.META_I : KeyCombo.CTRL_I);
        put(KeyComboTask.FORMAT_UNDERLINE, isMac ? KeyCombo.META_U : KeyCombo.CTRL_U);
        put(KeyComboTask.FORMAT_STRIKETHROUGH,
            isMac ? KeyCombo.META_SHIFT_5 : KeyCombo.CTRL_SHIFT_5);
        put(KeyComboTask.CREATE_LINK, isMac ? KeyCombo.META_K : KeyCombo.CTRL_K);
        put(KeyComboTask.CLEAR_LINK, isMac ? KeyCombo.META_SHIFT_K : KeyCombo.CTRL_SHIFT_K);
        put(KeyComboTask.SELECT_ALL, isMac ? KeyCombo.META_A : KeyCombo.CTRL_A);
        put(KeyComboTask.KEY_BACKSPACE, KeyCombo.BACKSPACE, KeyCombo.SHIFT_BACKSPACE);
        put(KeyComboTask.TEXT_DELETE, KeyCombo.DELETE, isMac ? KeyCombo.SHIFT_DELETE : null);
        put(KeyComboTask.TEXT_CUT, isMac ? KeyCombo.META_X : KeyCombo.CTRL_X,
            !isMac ? KeyCombo.SHIFT_DELETE : null);
        put(KeyComboTask.TEXT_COPY, isMac ? KeyCombo.META_C : KeyCombo.CTRL_C);
        put(KeyComboTask.TEXT_PASTE, isMac ? KeyCombo.META_V : KeyCombo.CTRL_V);
        put(KeyComboTask.TEXT_SUGGEST, KeyCombo.CTRL_SPACE);
        put(KeyComboTask.DONE_WITH_EDITING, KeyCombo.SHIFT_ENTER);
        put(KeyComboTask.CANCEL_EDITING, KeyCombo.ESC);
      }
    });

    // Conversation
    contexts.put(KeyComboContext.WAVE, new Context() {
      {
        put(KeyComboTask.EDIT_BLIP, KeyCombo.CTRL_E);
        put(KeyComboTask.REPLY_TO_BLIP, KeyCombo.CTRL_R, KeyCombo.CTRL_ENTER,
            KeyCombo.ENTER);
        put(KeyComboTask.CONTINUE_THREAD, KeyCombo.SHIFT_ENTER);
        put(KeyComboTask.DELETE_BLIP, KeyCombo.DELETE);
        put(KeyComboTask.DELETE_BLIP_WITHOUT_CONFIRMATION, KeyCombo.SHIFT_DELETE);
        put(KeyComboTask.POPUP_LINK, isMac ? KeyCombo.META_L : KeyCombo.CTRL_L);
        put(KeyComboTask.ADD_PARTICIPANT, isMac ? KeyCombo.META_I : KeyCombo.CTRL_I);
        put(KeyComboTask.ADD_TAG, isMac ? KeyCombo.META_V : KeyCombo.CTRL_V);
        put(KeyComboTask.SCROLL_TO_BEGIN, KeyCombo.HOME);
        put(KeyComboTask.SCROLL_TO_END, KeyCombo.END);
        put(KeyComboTask.SCROLL_TO_PREVIOUS_PAGE, KeyCombo.PAGE_UP);
        put(KeyComboTask.SCROLL_TO_NEXT_PAGE, KeyCombo.PAGE_DOWN);
        put(KeyComboTask.FOCUS_PREVIOUS_BLIP, KeyCombo.UP, KeyCombo.LEFT);
        put(KeyComboTask.FOCUS_NEXT_BLIP, KeyCombo.DOWN, KeyCombo.RIGHT);
        put(KeyComboTask.FOCUS_NEXT_UNREAD_BLIP, KeyCombo.SPACE);
      }
    });
  }

  public static Collection<KeyCombo> getKeyCombosByTask(KeyComboContext context, KeyComboTask task) {
    return getContext(context).getKeyCombosByTask(task);
  }

  public static KeyComboTask getTaskByKeyCombo(KeyComboContext context, KeyCombo keyCombo) {
    return getContext(context).getTaskByKeyCombo(keyCombo);
  }

  public static String getKeyComboHintByTask(KeyComboContext context, KeyComboTask task) {
    return getContext(context).getKeyComboHintByTask(task);
  }

  public static String getFirstKeyComboHintByTask(KeyComboContext context, KeyComboTask task) {
    return getContext(context).getFirstKeyComboHintByTask(task);
  }

  private static Context getContext(KeyComboContext context) {
    Context c = contexts.get(context);
    assert c != null : "Key combo context not found!";
    return c;
  }
}
