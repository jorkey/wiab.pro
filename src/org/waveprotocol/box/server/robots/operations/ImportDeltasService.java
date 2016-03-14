/**
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
package org.waveprotocol.box.server.robots.operations;

import com.google.common.collect.Maps;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.OperationRequest;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wave.api.ApiIdSerializer;
import com.google.wave.api.JsonRpcConstant.ParamsProperty;

import org.waveprotocol.box.server.robots.OperationContext;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.util.logging.Log;
import org.waveprotocol.wave.federation.Proto.ProtocolHashedVersion;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.id.*;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;
import org.waveprotocol.wave.clientserver.ReturnCode;

import java.util.Map;
import java.util.List;

/**
 * {@link OperationService} for the "importDeltas" operation.
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class ImportDeltasService implements OperationService {
  private static final Log LOG = Log.get(ImportDeltasService.class);
  private static final IdURIEncoderDecoder URI_CODEC = new IdURIEncoderDecoder(new JavaUrlCodec());
  private static final HashedVersionFactory HASH_FACTORY = new HashedVersionFactoryImpl(URI_CODEC);
  private final WaveletProvider waveletProvider;
  private final String waveDomain;

  @Inject
  public ImportDeltasService(WaveletProvider waveletProvider,
      @Named(CoreSettings.WAVE_SERVER_DOMAIN) final String waveDomain) {
    this.waveletProvider = waveletProvider;
    this.waveDomain = waveDomain;
  }

  @Override
  public void execute(OperationRequest operation, OperationContext context, ParticipantId participant)
      throws InvalidRequestException {
    try {
      WaveId waveId;
      WaveletId waveletId;
      try {
        waveId = ApiIdSerializer.instance().deserialiseWaveId(
            OperationUtil.<String>getRequiredParameter(operation, ParamsProperty.WAVE_ID));
        waveletId = ApiIdSerializer.instance().deserialiseWaveletId(
            OperationUtil.<String>getRequiredParameter(operation, ParamsProperty.WAVELET_ID));
      } catch (InvalidIdException ex) {
        throw new InvalidRequestException("Invalid id", operation, ex);
      }
      waveId = WaveId.of(waveDomain, waveId.getId());
      waveletId = WaveletId.of(waveDomain, waveletId.getId());
      List<byte[]> history =
          OperationUtil.getRequiredParameter(operation, ParamsProperty.RAW_DELTAS);
      WaveletName waveletName = WaveletName.of(waveId, waveletId);
      long importedFromVersion = -1;
      if (!history.isEmpty()) {
        for (byte[] deltaBytes : history) {
          ProtocolWaveletDelta delta;
          try {
            delta = ProtocolWaveletDelta.parseFrom(deltaBytes);
          } catch (InvalidProtocolBufferException ex) {
            throw new InvalidRequestException("Parse delta", operation, ex);
          }
          HashedVersion currentVersion;
          try {
            if (waveletProvider.checkExistence(waveletName)) {
              currentVersion = waveletProvider.getLastModifiedVersion(waveletName);
            } else {
              currentVersion = HASH_FACTORY.createVersionZero(waveletName);
            }
          } catch (WaveServerException ex) {
            throw new InvalidRequestException("Get current version", operation, ex);
          }
          if (currentVersion.getVersion() == delta.getHashedVersion().getVersion()) {
            if (importedFromVersion == -1) {
              importedFromVersion = currentVersion.getVersion();
            }
            ProtocolWaveletDelta newDelta;
            try {
              newDelta = setVersionHash(delta, currentVersion, waveletName);
            } catch (InvalidParticipantAddress ex) {
              throw new InvalidRequestException("Convert delta", operation, ex);
            }
            final StringBuffer error = new StringBuffer();
            waveletProvider.submitRequest(waveletName, newDelta,
                new WaveletProvider.SubmitRequestCallback() {

                  @Override
                  public void onSuccess(int operationsApplied, HashedVersion hashedVersionAfterApplication,
                      long applicationTimestamp) {
                  }

                  @Override
                  public void onFailure(ReturnCode responseCode, String errorMessage) {
                    error.append(responseCode.toString() + " : " + errorMessage);
                  }
                });
            if (error.length() != 0) {
              context.constructErrorResponse(operation, error.toString());
              return;
            }
          } else if (importedFromVersion != -1) {
            context.constructErrorResponse(operation, "Expected wavelet version "
                + delta.getHashedVersion().getVersion() + ", but current version is " + currentVersion + "."
                + "Possibly wavelet is modified during import.");
            return;
          }
        }
      }
      Map<ParamsProperty, Object> response = Maps.<ParamsProperty, Object> newHashMap();
      response.put(ParamsProperty.IMPORTED_FROM_VERSION, importedFromVersion);
      context.constructResponse(operation, response);
    } catch (Exception ex) {
      LOG.severe("Import error", ex);
      throw ex;
    }
  }

  /**
   * Sets correct version hash to delta.
   *
   * @param delta the source delta.
   * @param waveletSnapshot to append delta.
   * @param waveletName name of wavelet.
   * @return the delta to import.
   * @throws InvalidParticipantAddress deserialize of participant error.
   */
  ProtocolWaveletDelta setVersionHash(ProtocolWaveletDelta delta,
      HashedVersion currentVersion, WaveletName waveletName) throws InvalidParticipantAddress {
    ProtocolWaveletDelta.Builder newDelta = ProtocolWaveletDelta.newBuilder(delta);
    ProtocolHashedVersion ver = ProtocolHashedVersion.newBuilder(delta.getHashedVersion()).
        setHistoryHash(ByteString.copyFrom(currentVersion.getHistoryHash())).
        build();
    newDelta.setHashedVersion(ver);
    return newDelta.build();
  }
}
