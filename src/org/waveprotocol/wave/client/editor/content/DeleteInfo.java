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

package org.waveprotocol.wave.client.editor.content;

import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for a bunch of deleted stuff, for diff highlighting.
 *
 * @author danilatos@google.com (Daniel Danilatos)
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public final class DeleteInfo {

  /**
   * Wrapper for one deleted DOM element.
   */
  public static final class Item {

    /** Deleted DOM element. */
    private final Element element;

    /** Reply id, if the element is an anchor; null otherwise. */
    private final String replyId;

    public Item(Element element, String replyId) {
      this.element = element;
      this.replyId = replyId;
    }

    public Element getElement() {
      return element;
    }

    public boolean isReply() {
      return replyId != null;
    }

    public String getReplyId() {
      return replyId;
    }

    @Override
    public String toString() {
      return "[elementId:" + element.getId() + ", replyId: " + replyId + "]";
    }
  }

  /**
   * Transformer of delete info-s.
   */
  public interface Transformer {

    /**
     * Transforms one delete info into another.
     *
     * @param initialInfo initial delete info to be processed
     * @return processed delete info or initial delete info (if no changes were made)
     */
    DeleteInfo transform(DeleteInfo initialInfo);
  }

  /**
   * Transformer of delete info-s by filtering out some items.
   */
  public abstract static class FilteredTransformer implements Transformer {

    @Override
    public DeleteInfo transform(DeleteInfo initialInfo) {
      List<Item> newItems = new ArrayList<>();
      boolean changed = false;
      for (Item item : initialInfo.getItems()) {
        if (isRetained(item)) {
          newItems.add(item);
        } else {
          changed = true;
        }
      }
      DeleteInfo result = initialInfo;
      if (changed) {
        result = newItems.isEmpty() ? null : new DeleteInfo(newItems);
      }
      return result;
    }

    /**
     * Returns true, if the item should be retained after filtering.
     *
     * @param item item to be check
     */
    public abstract boolean isRetained(Item item);
  }

  /** Items contained within this info. */
  private final List<Item> items;

  public DeleteInfo() {
    items = new ArrayList<>();
  }

  protected DeleteInfo(List<Item> items) {
    this.items = items;
  }

  public List<Item> getItems() {
    return items;
  }

  /**
   * The HTML of the deleted content.
   */
  public List<Element> getDeletedHtmlElements() {
    List<Element> elements = new ArrayList<>();
    for (Item item : items) {
      elements.add(item.getElement());
    }
    return elements;
  }  
  
  public void add(Element e) {
    items.add(new Item(e, null));
  }

  public void add(Element e, String replyId) {
    items.add(new Item(e, replyId));
  }

  public void addAll(DeleteInfo other) {
    items.addAll(other.getItems());
  }

  @Override
  public String toString() {
    return items.toString();
  }
}