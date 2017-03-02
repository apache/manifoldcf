package org.apache.manifoldcf.crawler.connectors.webcrawler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.crawler.connectors.webcrawler.WebcrawlerConnector.DocumentURLFilter;
import org.apache.manifoldcf.crawler.connectors.webcrawler.WebcrawlerConnector.ProcessActivityHTMLHandler;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.junit.Before;
import org.junit.Test;

public class ProcessActivityHTMLHandlerTest {

  private WebcrawlerConnector webcrawler = new WebcrawlerConnector();

  private Map<String, String> metaRobotsNoindexNofollow = new HashMap<>();

  @Before
  public void setup() {
    metaRobotsNoindexNofollow.put("name", "robots");
    metaRobotsNoindexNofollow.put("content", "noindex,nofollow");
  }

  @Test
  public void testNoteMetaTag_robotsInstructionsAreObeyed() throws ManifoldCFException {
    IProcessActivity mockActivity = mock(IProcessActivity.class);
    DocumentURLFilter filter = mock(DocumentURLFilter.class);
    ProcessActivityHTMLHandler sut = webcrawler.new ProcessActivityHTMLHandler("id", mockActivity, filter, WebcrawlerConnector.META_ROBOTS_ALL);
    sut.noteMetaTag(metaRobotsNoindexNofollow);
    assertFalse(sut.allowIndex);
    assertFalse(sut.allowFollow);
  }

  @Test
  public void testNoteMetaTag_robotsInstructionsCanBeIgnored() throws ManifoldCFException {
    IProcessActivity mockActivity = mock(IProcessActivity.class);
    DocumentURLFilter filter = mock(DocumentURLFilter.class);
    ProcessActivityHTMLHandler sut = webcrawler.new ProcessActivityHTMLHandler("id", mockActivity, filter, WebcrawlerConnector.META_ROBOTS_NONE);
    sut.noteMetaTag(metaRobotsNoindexNofollow);
    assertTrue(sut.allowIndex);
    assertTrue(sut.allowFollow);
  }

}
