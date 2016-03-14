package org.waveprotocol.box.server.persistence.html;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.persistence.file.FileUtils;
import org.waveprotocol.wave.model.id.WaveId;

/**
 *
 * @author akaplanov@gmai.com (Andrew Kaplanov)
 */
public class WaveHtmlStore {
  public static final String HTML_FILE_SUFFIX = ".html";

  private final File directory;

  @Inject
  public WaveHtmlStore(@Named(CoreSettings.HTML_STORE_DIRECTORY) String directory) {
    this.directory = new File(directory);
  }

  public void writeHtml(WaveId waveId, String html) throws IOException {
    if (!directory.exists()) {
      directory.mkdirs();
    }
    OutputStream out = new FileOutputStream(htmlFile(directory, waveId));
    try {
      out.write(html.getBytes("utf8"));
    } finally {
      out.close();
    }
  }

  public String readHtml(WaveId waveId) throws IOException {
    File file = htmlFile(directory, waveId);
    if (file.exists()) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      InputStream in = new FileInputStream(htmlFile(directory, waveId));
      try {
        byte[] buf = new byte[1024];
        for (;;) {
          int ret = in.read(buf);
          if (ret < 0) {
            break;
          }  
          bytes.write(buf, 0, ret);
        }
      } finally {
        in.close();
      }
      return new String(bytes.toByteArray(), "utf8");
    }
    return null;
  }

  public static File htmlFile(File htmlDirectory, WaveId waveId) {
     return new File(htmlDirectory, FileUtils.waveIdToPathSegment(waveId) + ".html");
  }
}
