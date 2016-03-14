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
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.operation.OperationException;

import org.waveprotocol.wave.util.logging.Log;

import java.util.Map;

/**
 * Implements the "robot.folderAction" operations.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 */
public class FolderActionService implements OperationService {
  private static final Log LOG = Log.get(FolderActionService.class);

  public enum ModifyHowType {

    MARK_AS_READ("markAsRead"),
    MARK_AS_UNREAD("markAsUnread"),
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

  public static FolderActionService create() {
    return new FolderActionService();
  }

  @Override
  public void execute(OperationRequest operation, OperationContext context,
      ParticipantId participant) throws InvalidRequestException {

    String modifyHow = OperationUtil.getRequiredParameter(operation, ParamsProperty.MODIFY_HOW);
    String blipId = OperationUtil.getOptionalParameter(operation, ParamsProperty.BLIP_ID);

    SupplementedWave supplement;
    try {
      supplement = buildSupplement(operation, context, participant);
    } catch (OperationException ex) {
      LOG.severe("Supplement building error", ex);
      context.constructErrorResponse(operation, "Supplement building error");
      return;
    }

    if (modifyHow.equals(ModifyHowType.MARK_AS_READ.getValue())) {
      if (blipId == null || blipId.isEmpty()) {
        supplement.markAsRead();
      } else {
        ObservableConversation conversation =
            context.openConversation(operation, participant).getRoot();
        ConversationBlip blip = conversation.getBlip(blipId);
        supplement.markAsRead(blip);
      }
    } else if (modifyHow.equals(ModifyHowType.MARK_AS_UNREAD.getValue())) {
      supplement.markAsUnread();
    } else if (modifyHow.equals(ModifyHowType.ARCHIVE.getValue())) {
      supplement.archive();
    } else if (modifyHow.equals(ModifyHowType.INBOX.getValue())) {
      supplement.inbox();
    } else {
      throw new UnsupportedOperationException("Unsupported folder action: " + modifyHow);
    }
    // Construct empty response.
    Map<ParamsProperty, Object> data = Maps.newHashMap();
    context.constructResponse(operation, data);
  }
}
