/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.box.search.query;

import org.waveprotocol.box.server.util.regexp.RegExpWrapFactoryImpl;
import org.waveprotocol.box.server.util.testing.TestingConstants;

import junit.framework.TestCase;

/**
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class QueryParserTest extends TestCase implements TestingConstants {

  QueryParser parser = new QueryParser(new RegExpWrapFactoryImpl());
      
  @Override
  protected void setUp() throws Exception {
  }

  public void testInboxCondition() {
    SearchQuery query = parser.parseQuery("in:inbox");
    assertEquals(1, query.getConditions().size());
    assertTrue(query.getConditions().get(0).isInbox());
    assertTrue(query.isInbox());
  }

  public void testArchiveCondition() {
    SearchQuery query = parser.parseQuery("in:archive");
    assertEquals(1, query.getConditions().size());
    assertTrue(query.getConditions().get(0).isArchive());
    assertTrue(query.isArchive());
  }

  public void testPublicCondition() {
    SearchQuery query = parser.parseQuery("with:@");
    assertEquals(1, query.getConditions().size());
    assertTrue(query.getConditions().get(0).isPublic());
    assertTrue(query.isPublic());
  }
  
  public void testTwoConditions() {
    SearchQuery query = parser.parseQuery("in:inbox qwerty");
    assertEquals(2, query.getConditions().size());
    assertTrue(query.isInbox());
  
    query = parser.parseQuery("qwerty in:inbox");
    assertEquals(2, query.getConditions().size());
    assertTrue(query.isInbox());
  }

  public void testContentCondition() {
    SearchQuery query = parser.parseQuery("qwerty");
    assertEquals(1, query.getConditions().size());
    assertEquals(QueryCondition.Field.CONTENT, query.getConditions().get(0).getField());
    assertEquals("qwerty", query.getConditions().get(0).getValue());

    query = parser.parseQuery("content:qwerty");
    assertEquals(1, query.getConditions().size());
    assertEquals(QueryCondition.Field.CONTENT, query.getConditions().get(0).getField());
    assertEquals("qwerty", query.getConditions().get(0).getValue());
  }

  public void testTwoContentConditions() {
    SearchQuery query = parser.parseQuery("qwe 123");
    assertEquals(2, query.getConditions().size());
    assertEquals(QueryCondition.Field.CONTENT, query.getConditions().get(0).getField());
    assertEquals("qwe", query.getConditions().get(0).getValue());
    assertEquals(QueryCondition.Field.CONTENT, query.getConditions().get(1).getField());
    assertEquals("123", query.getConditions().get(1).getValue());
  }

  public void testContentPhraseCondition() {
    SearchQuery query = parser.parseQuery("\"Hello world\"");
    assertEquals(1, query.getConditions().size());
    assertTrue(query.getConditions().get(0).isPhrase());
    assertEquals("Hello world", query.getConditions().get(0).getValue());

    query = parser.parseQuery("content:\"Hello world\"");
    assertEquals(1, query.getConditions().size());
    assertTrue(query.getConditions().get(0).isPhrase());
    assertEquals("Hello world", query.getConditions().get(0).getValue());
  }

  public void testContentPhraseWithOtherCondition() {
    SearchQuery query = parser.parseQuery("\"Hello world\" in:inbox");
    assertEquals(query.getConditions().size(), 2);
    assertTrue(query.getConditions().get(0).isPhrase());
    assertEquals(query.getConditions().get(0).getValue(), "Hello world");
    assertTrue(query.getConditions().get(1).isInbox());
    assertTrue(query.isInbox());

    query = parser.parseQuery("content:\"Hello world\" in:inbox");
    assertEquals(query.getConditions().size(), 2);
    assertTrue(query.getConditions().get(0).isPhrase());
    assertEquals(query.getConditions().get(0).getValue(), "Hello world");
    assertTrue(query.getConditions().get(1).isInbox());
    assertTrue(query.isInbox());
  }

  public void testConditionWithUnknownField() {
    SearchQuery query = parser.parseQuery("qwe:asdfg");
    assertEquals(query.getConditions().size(), 1);
    assertEquals(query.getConditions().get(0).getField(), QueryCondition.Field.CONTENT);
    assertEquals(query.getConditions().get(0).getValue(), "qwe:asdfg");
    assertTrue(query.getConditions().get(0).isPhrase());

    query = parser.parseQuery("qwe:\"Hello world\"");
    assertEquals(query.getConditions().size(), 1);
    assertEquals(query.getConditions().get(0).getField(), QueryCondition.Field.CONTENT);
    assertEquals(query.getConditions().get(0).getValue(), "qwe:\"Hello world\"");
    assertTrue(query.getConditions().get(0).isPhrase());
  }
}
