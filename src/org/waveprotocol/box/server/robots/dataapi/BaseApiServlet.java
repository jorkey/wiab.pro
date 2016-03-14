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

package org.waveprotocol.box.server.robots.dataapi;

import com.google.common.collect.Lists;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.JsonRpcResponse;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.RobotSerializer;
import com.google.wave.api.data.converter.EventDataConverterManager;
import com.google.wave.api.impl.GsonFactory;

import net.oauth.OAuthAccessor;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import net.oauth.OAuthValidator;

import org.waveprotocol.box.server.robots.OperationContext;
import org.waveprotocol.box.server.robots.OperationContextImpl;
import org.waveprotocol.box.server.robots.OperationResults;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.robots.util.LoggingRequestListener;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.attachment.AttachmentMetadata;
import org.waveprotocol.box.server.attachment.AttachmentService;
import org.waveprotocol.box.server.persistence.AttachmentStore.AttachmentData;
import org.waveprotocol.box.server.persistence.AttachmentUtil;
import org.waveprotocol.box.server.robots.active.ActiveApiServlet;
import org.waveprotocol.box.server.waveserver.WaveServerException;

import org.waveprotocol.wave.media.model.AttachmentId;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.util.logging.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.oauth.server.HttpRequestMessage;
import org.apache.commons.codec.binary.Base64;

/**
 * The base {@link HttpServlet} for {@link DataApiServlet} and
 * {@link ActiveApiServlet}.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 * @author vega113@gmail.com (Yuri Z.)
 */
@SuppressWarnings("serial")
public abstract class BaseApiServlet extends HttpServlet {

  private static final Log LOG = Log.get(BaseApiServlet.class);
  private static final WaveletProvider.SubmitRequestCallback LOGGING_REQUEST_LISTENER =
      new LoggingRequestListener(LOG);
  private static final String JSON_CONTENT_TYPE = "application/json";

  private static final String ATTACHMENT_PARAM = "attachment";

  private final RobotSerializer robotSerializer;
  private final EventDataConverterManager converterManager;
  private final WaveletProvider waveletProvider;
  private final OperationServiceRegistry operationRegistry;
  private final ConversationUtil conversationUtil;
  private final OAuthValidator validator;
  private final AttachmentService attachmentService;

  public BaseApiServlet(RobotSerializer robotSerializer,
      EventDataConverterManager converterManager, WaveletProvider waveletProvider,
      OperationServiceRegistry operationRegistry, ConversationUtil conversationUtil,
      OAuthValidator validator, AttachmentService attachmentService) {
    this.robotSerializer = robotSerializer;
    this.converterManager = converterManager;
    this.waveletProvider = waveletProvider;
    this.conversationUtil = conversationUtil;
    this.operationRegistry = operationRegistry;
    this.validator = validator;
    this.attachmentService = attachmentService;
  }

  /**
   *  Executes operations.
   *
   * @param req the request.
   * @param resp the response.
   * @param participant the author for which to perform the robot operations.
   * @throws IOException if encountered errors during writing of a response.
   */
  protected final void processOpsRequest(HttpServletRequest req, HttpServletResponse resp, ParticipantId participant) throws IOException {
    String apiRequest;
    try {
      // message.readBodyAsString() doesn't work due to a NPE in the OAuth
      // libraries.
      BufferedReader reader = req.getReader();
      apiRequest = reader.readLine();
    } catch (IOException e) {
      LOG.warning("Unable to read the incoming request", e);
      throw e;
    }

    List<OperationRequest> operations;
    if (LOG.isFineLoggable()) {
      LOG.fine("Received the following Json: " + apiRequest);
    }
    try {
      operations = robotSerializer.deserializeOperations(apiRequest);
    } catch (InvalidRequestException e) {
      LOG.info("Unable to parse Json to list of OperationRequests: " + apiRequest);
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "Unable to parse Json to list of OperationRequests: " + apiRequest);
      return;
    }

    // Create an unbound context.
    ProtocolVersion version = OperationUtil.getProtocolVersion(operations);
    OperationContextImpl context = new OperationContextImpl(
        waveletProvider, converterManager.getEventDataConverter(version), conversationUtil);

    executeOperations(context, operations, participant);
    handleResults(operations, context, resp, version);
  }

  /**
   *  Gets attachment.
   *
   * @param req the request.
   * @param resp the response.
   * @param participant the author for which to perform the robot operations.
   * @throws IOException if encountered errors during writing of a response.
   */
  protected final void processGetAttachmentRequest(HttpServletRequest req, HttpServletResponse resp,
      ParticipantId participant) throws IOException, ServletException {
    String attachmentParam = req.getParameter(ATTACHMENT_PARAM);
    if (attachmentParam != null) {
      AttachmentId attachmentId;
      try {
        attachmentId = AttachmentId.deserialise(attachmentParam);
      } catch (InvalidIdException ex) {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      AttachmentMetadata metadata = attachmentService.getMetadata(attachmentId);
      if (metadata == null) {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      try {
        WaveletName waveletName = AttachmentUtil.waveRef2WaveletName(metadata.getWaveRef());
        if (!waveletProvider.checkExistence(waveletName)) {
          resp.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
        if (!waveletProvider.checkAccessPermission(waveletName, participant)) {
          resp.sendError(HttpServletResponse.SC_FORBIDDEN);
          return;
        }
      } catch (WaveServerException e) {
        LOG.warning("Problem while authorizing user: " + participant + " for wavelet: " + metadata.getWaveRef(), e);
      }

      AttachmentData data = attachmentService.getAttachment(attachmentId);
      if (data == null) {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      resp.setContentType(metadata.getMimeType());
      resp.setContentLength((int)data.getSize());
      resp.setHeader("Content-Disposition", "attachment; filename=\"" +
          encodeFileName(metadata.getFileName()) + "\"");
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.setDateHeader("Last-Modified", Calendar.getInstance().getTimeInMillis());
      AttachmentUtil.writeTo(data.getInputStream(), resp.getOutputStream());

      LOG.info("Fetched attachment with id '" + attachmentId + "'");
    }
  }

	static private String encodeFileName(String fileName) throws UnsupportedEncodingException {
  	return "=?UTF-8?B?" + (new String(Base64.encodeBase64(fileName.getBytes("UTF-8")))) + "?=";
	}

  protected boolean validateMessage(HttpServletRequest req, OAuthAccessor accessor) throws IOException {
    OAuthMessage message = new HttpRequestMessage(req, req.getRequestURL().toString());

    try {
      validator.validateMessage(message, accessor);
    } catch (OAuthException e) {
      LOG.info("The message does not conform to OAuth", e);
      return false;
    } catch (URISyntaxException e) {
      LOG.info("The message URL is invalid", e);
      return false;
    }

    return true;
  }

  private static AttachmentId getAttachmentIdFromRequest(HttpServletRequest request) {
    if (request.getPathInfo().length() == 0) {
      return null;
    }
    String id = getAttachmentIdStringFromRequest(request);
    try {
      return AttachmentId.deserialise(id);
    } catch (InvalidIdException ex) {
      LOG.log(Level.SEVERE, "Deserialize attachment Id " + id, ex);
      return null;
    }
  }

  private static String getAttachmentIdStringFromRequest(HttpServletRequest request) {
    // Discard the leading '/' in the pathinfo.
    return request.getPathInfo().substring(1);
  }

  /**
   * Executes operations in the given context.
   *
   * @param context the context to perform the operations in.
   * @param operations the operations to perform.
   * @param author the author for which to perform the robot operations.
   */
  private void executeOperations(
      OperationContext context, List<OperationRequest> operations, ParticipantId author) {
    for (OperationRequest operation : operations) {
      OperationUtil.executeOperation(operation, operationRegistry, context, author);
    }
  }

  /**
   * Handles an {@link OperationResults} by submitting the deltas that are
   * generated and writing a response to the robot.
   *
   * @param operations the operations
   * @param results the results of the operations performed.
   * @param resp the servlet to write the response in.
   * @param version the version of the protocol to use for writing a response.
   * @throws IOException if the response can not be written.
   */
  private void handleResults(
      List<OperationRequest> operations, OperationResults results,
      HttpServletResponse resp, ProtocolVersion version)
      throws IOException {
    OperationUtil.submitDeltas(results, waveletProvider, LOGGING_REQUEST_LISTENER);

    // Ensure that responses are returned in the same order as corresponding
    // requests.
    LinkedList<JsonRpcResponse> responses = Lists.newLinkedList();
    for (OperationRequest operation : operations) {
      String opId = operation.getId();
      JsonRpcResponse response = results.getResponses().get(opId);
      responses.addLast(response);
    }

    String jsonResponse =
        robotSerializer.serialize(responses, GsonFactory.JSON_RPC_RESPONSE_LIST_TYPE, version);
    LOG.fine("Returning the following Json: " + jsonResponse);

    // Write the response back through the HttpServlet
    try {
      resp.setContentType(JSON_CONTENT_TYPE);
      PrintWriter writer = resp.getWriter();
      writer.append(jsonResponse);
      writer.flush();
      resp.setStatus(HttpServletResponse.SC_OK);
    } catch (IOException e) {
      LOG.severe("IOException during writing of a response", e);
      throw e;
    }
  }
}
