package org.apache.manifoldcf.agents.output.amazoncloudsearch.tests;

import static org.junit.Assert.*;

import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.amazoncloudsearch.AmazonCloudSearchConnector;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.junit.Test;

public class AmazonCloudSearchConnectorTest {

  @Test
  public void testRemoveDocument() {
    
    AmazonCloudSearchConnector conn = new AmazonCloudSearchConnector();
    String documentURI = "out.json_3489"; //日本料理
    
    try {
      conn.removeDocument(documentURI, null, null);
    } catch (ManifoldCFException e) {
      e.printStackTrace();
      fail();
    } catch (ServiceInterruption e) {
      e.printStackTrace();
      fail();
    }
  }
}
