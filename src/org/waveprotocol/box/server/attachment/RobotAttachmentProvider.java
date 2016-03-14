package org.waveprotocol.box.server.attachment;

import com.google.inject.Inject;
import com.google.wave.api.data.ApiAttachment;
import java.io.IOException;
import org.waveprotocol.box.attachment.AttachmentMetadata;
import org.waveprotocol.wave.media.model.AttachmentId;
import org.waveprotocol.wave.model.id.InvalidIdException;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class RobotAttachmentProvider implements ApiAttachment.Provider {
  private final AttachmentService attachmentService;

  @Inject
  public RobotAttachmentProvider(AttachmentService attachmentService) {
    this.attachmentService = attachmentService;
  }

  @Override
  public ApiAttachment getAttachment(String attachmentId) throws IOException, InvalidIdException {
    final AttachmentMetadata metadata = attachmentService.getMetadata(AttachmentId.deserialise(attachmentId));
    if (metadata == null) {
      return null;
    }
    return new ApiAttachment() {
      @Override
      public String getMimeType() {
        return metadata.getMimeType();
      }

      @Override
      public String getFileName() {
        return metadata.getFileName();
      }
    };
  }

}
