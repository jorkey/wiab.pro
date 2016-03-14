package org.waveprotocol.wave.client.widget.dialog;

import com.google.gwt.user.client.Timer;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

/**
 * Dialog activator.
 * When wait period is over, calls setVisible(true).
 * If cancel() is called, calls setVisible(false).
 * If submit() is called, calls setVisible(false) and calls execute().
 *
  * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class DialogActivator {

  private final Timer waitTimer =
      new Timer() {

        @Override
        public void run() {
          cancelWait();
          setVisible(true);
        }
      };
  private final UniversalPopup popup;

  private boolean waitActivated = false;
  private boolean shown = false;

  public DialogActivator(UniversalPopup popup) {
    this.popup = popup;
  }

  public void start(int waitDelay) {
    if (!waitActivated && !shown) {
      waitTimer.schedule(waitDelay);
      waitActivated = true;
    }
  }

  public void submit() {
    if (waitActivated || shown) {
      cancelWait();
      setVisible(false);
    }
  }

  public void submit(DialogButton button) {
    if (waitActivated || shown) {
      cancelWait();
      setVisible(false);
      button.execute();
    }
  }

  public void setVisible(boolean visible) {
    if (shown != visible) {
      if (popup != null) {
        if (visible) {
          popup.show();
        } else {
          popup.hide();
        }
      }
      shown = visible;
    }
  }

  private void cancelWait() {
    if (waitActivated) {
      waitTimer.cancel();
      waitActivated = false;
    }
  }
}
