package org.apache.manifoldcf.crawler.connectors.cmis.tests;

import org.apache.manifoldcf.crawler.connectors.cmis.CmisRepositoryConnectorUtils;
import org.junit.Ignore;
import org.junit.Test;

public class CheckObjectIDTest {

  @Ignore
  @Test
  public void testSimple() {
    String cmisQuery = " select cmis:name from cmis:folder where cmis:name='Colacem'";
    cmisQuery = CmisRepositoryConnectorUtils.getCmisQueryWithObjectId(cmisQuery);
    System.out.println(cmisQuery);
    System.exit(0);
  }
  
}
