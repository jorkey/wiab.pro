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

package org.waveprotocol.box.server.robots.operations;

import static org.waveprotocol.box.server.robots.util.OperationUtil.buildSupplement;

import com.google.common.collect.Maps;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.JsonRpcConstant.ParamsProperty;
import com.google.wave.api.OperationRequest;

import org.waveprotocol.box.server.robots.OperationContext;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;

/**
 * Implements the "robot.folderActionForAll" operations.
 *
 * @author akaplanov@gmail.com(Andrew Kaplanov)
 */
public class FolderActionForAllService implements OperationService {
  static private final Logger LOG = Logger.getLogger(FolderActionForAllService.class.getName());

  public enum ModifyHowType {

    ARCHIVE("archive"),
    INBOX("inbox");

    private final String value;

    private ModifyHowType(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  public static FolderActionForAllService create() {
    return new FolderActionForAllService();
  }

  @Override
  public void execute(OperationRequest operation, OperationContext context,
      ParticipantId participant) throws InvalidRequestException {

    String modifyHow = OperationUtil.getRequiredParameter(operation, ParamsProperty.MODIFY_HOW);

    OpBasedWavelet wavelet = context.openWavelet(operation, participant);

    String errorMsg = null;

    for (ParticipantId participantId : wavelet.getParticipantIds()) {
      try {
        SupplementedWave supplement = buildSupplement(operation, context, participantId);
        if (modifyHow.equals(ModifyHowType.ARCHIVE.getValue())) {
          supplement.archive();
        } else if (modifyHow.equals(ModifyHowType.INBOX.getValue())) {
          supplement.inbox();
        } else {
          throw new UnsupportedOperationException("Unsupported folder action: " + modifyHow);
        }
      } catch (InvalidRequestException | OperationException ex) {
        LOG.log(Level.WARNING, "Folder operation error", ex);
        if (errorMsg != null) {
          errorMsg = ex.getLocalizedMessage();
        }
      }
    }

    if (errorMsg != null) {
      context.constructErrorResponse(operation, errorMsg);
    } else {
      Map<ParamsProperty, Object> data = Maps.newHashMap();
      context.constructResponse(operation, data);
    }
  }
}
