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

package org.waveprotocol.wave.model.document.dom;

import java.util.Map;
import org.waveprotocol.wave.model.document.DocumentParser;

/**
 * A provider for RawDocuments.  RawDocuments can be created either by
 * specifying the properties for the document element, or by parsing an XML
 * fragment.
 *
 */
public class DomDocumentProviderImpl<
    N, E extends N, T extends N, D extends DomDocument<N, E, T>>
    implements DomDocument.Provider<D> {

  /** Builder that constructs the document. */
  private final DomDocument.Factory<D> builder;

  /** Parser that parses documents. */
  private final DocumentParser<D> parser;

  /**
   * Creates a document provider from a builder.
   *
   * @param builder  builder
   * @return new provider.
   */
  public static <N, E extends N, T extends N, D extends DomDocument<N, E, T>>
      DomDocumentProviderImpl<N, E, T, D> create(DomDocument.Factory<D> builder) {
    return new DomDocumentProviderImpl<N, E, T, D>(builder);
  }

  /**
   * Constructs a document provider from a builder.
   *
   * @param builder  builder
   */
  private DomDocumentProviderImpl(DomDocument.Factory<D> builder) {
    this.builder = builder;
    // TODO(user): Perhaps we can get rid of these parsers and copiers. A
    // RawDocument is almost certainly the wrong level to do something like
    // this.
    this.parser = DomDocumentParserImpl.create(builder);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public D create(String tagName, Map<String, String> attributes) {
    return builder.create(tagName, attributes);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public D parse(String xml) {
    return parser.parse(xml);
  }

}
