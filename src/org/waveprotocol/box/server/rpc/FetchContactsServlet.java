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
package org.waveprotocol.box.server.rpc;

import com.google.gson.JsonElement;
import com.google.inject.Inject;

import org.waveprotocol.box.server.contact.Contact;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.rpc.ProtoSerializer.SerializationException;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.contact.ContactsProto;
import org.waveprotocol.box.contact.ContactsProto.ContactResponse;
import org.waveprotocol.box.contact.ContactsProto.ContactRequest;
import org.waveprotocol.box.server.contact.ContactManager;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.util.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * Servlet allows user to get his contacts.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
@SuppressWarnings("serial")
public final class FetchContactsServlet extends HttpServlet {
  private final static Log LOG = Log.get(FetchContactsServlet.class);

  final static long NEW_CALLS_PERIOD_SEC = 7*24*60*60; // 7 days.
  final static long CALL_IN_TIME_SCORE_SEC = 24*60*60; // 1 days.

  private final SessionManager sessionManager;
  private final ContactManager contactManager;
  private final ProtoSerializer serializer;

  /**
   * Extracts contact query params from request.
   *
   * @param req the request.
   * @return the ContactRequest with query data.
   * @throws UnsupportedEncodingException if the request parameters encoding is invalid.
   */
  private static ContactRequest parseContactRequest(HttpServletRequest req) throws UnsupportedEncodingException {
    long timestamp = Long.parseLong(URLDecoder.decode(req.getParameter("timestamp"), "UTF-8"));
    ContactRequest profileRequest =
        ContactRequest.newBuilder().setTimestamp(timestamp).build();
    return profileRequest;
  }

  @Inject
  public FetchContactsServlet(SessionManager sessionManager, ContactManager contactManager,
      ProtoSerializer serializer) {
    this.sessionManager = sessionManager;
    this.contactManager = contactManager;
    this.serializer = serializer;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId participantId = sessionManager.getLoggedInUser(req.getSession(false));
    if (participantId == null) {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    ContactRequest contactRequest = parseContactRequest(req);
    long clientTimestamp = contactRequest.getTimestamp();
    List<Contact> contacts = new ArrayList<Contact>();
    try {
      contacts = contactManager.getContacts(participantId, clientTimestamp);
    } catch (PersistenceException ex) {
      LOG.severe("Get contacts error", ex);
    }
    resp.setContentType("application/json; charset=utf8");
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setHeader("Cache-Control", "no-store");
    try {
      long currentTimestamp = Calendar.getInstance().getTimeInMillis();
      ContactResponse response = serializeContacts(contacts, currentTimestamp);
      JsonElement responseJson = serializer.toJson(response);
      resp.getWriter().append(responseJson.toString());
    } catch (SerializationException ex) {
      LOG.severe("Contact serialization error", ex);
    }
  }

  private ContactResponse serializeContacts(List<Contact> contacts, long timestamp) {
    ContactResponse.Builder contactsBuilder = ContactsProto.ContactResponse.newBuilder();
    for (Contact contact : contacts) {
      ContactResponse.Contact.Builder contactBuilder = ContactResponse.Contact.newBuilder();
      contactBuilder.setParticipant(contact.getParticipantId().getAddress());
      contactBuilder.setScore(timestamp + contactManager.getScoreBonusAtTime(contact, timestamp));
      contactsBuilder.addContact(contactBuilder);
    }
    contactsBuilder.setTimestamp(timestamp);
    ContactResponse response = contactsBuilder.build();
    return response;
  }
}
