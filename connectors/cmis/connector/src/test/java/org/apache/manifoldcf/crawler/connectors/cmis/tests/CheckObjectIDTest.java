package org.apache.manifoldcf.crawler.connectors.cmis.tests;

import org.apache.manifoldcf.crawler.connectors.cmis.CmisRepositoryConnectorUtils;

public class CheckObjectIDTest {

  public static void main(String[] args) {
    String cmisQuery = " select cmis:name from cmis:folder where cmis:name='Colacem'";
    cmisQuery = CmisRepositoryConnectorUtils.getCmisQueryWithObjectId(cmisQuery);
    System.out.println(cmisQuery);
    System.exit(0);
  }
  
}
