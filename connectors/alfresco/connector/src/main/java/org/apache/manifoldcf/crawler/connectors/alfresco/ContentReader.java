package org.apache.manifoldcf.crawler.connectors.alfresco;

import java.io.InputStream;
import java.rmi.RemoteException;

import org.alfresco.webservice.authentication.AuthenticationFault;
import org.alfresco.webservice.content.Content;
import org.alfresco.webservice.content.ContentFault;
import org.alfresco.webservice.content.ContentServiceSoapBindingStub;
import org.alfresco.webservice.types.Predicate;
import org.alfresco.webservice.util.AuthenticationDetails;
import org.alfresco.webservice.util.AuthenticationUtils;
import org.alfresco.webservice.util.ContentUtils;
import org.alfresco.webservice.util.WebServiceFactory;
import org.apache.manifoldcf.crawler.system.Logging;

public class ContentReader {

  /**
   * Read the binary for the current content
   * @param predicate
   * @return an unique binary for content
   */
  public static Content read(String username, String password, AuthenticationDetails session, Predicate predicate, String contentProperty) {
    Content[] resultBinary = null;
    try {
      AuthenticationUtils.startSession(username, password);
      session = AuthenticationUtils.getAuthenticationDetails();
      ContentServiceSoapBindingStub contentService = WebServiceFactory.getContentService();
      resultBinary = contentService.read(predicate, contentProperty);
    } catch (ContentFault e) {
        Logging.connectors
        .error(
            "Alfresco: Content fault exception error during getting the content binary in processDocuments. " +
            "Node: "+predicate.getNodes()[0].getPath() + ". "
                + e.getMessage(), e);
    } catch (RemoteException e) {
        Logging.connectors
        .error(
            "Alfresco: Remote exception error during getting the content binary in processDocuments. " +
            "Node: "+predicate.getNodes()[0].getPath() + ". "
                + e.getMessage(), e);
    } finally{
      AuthenticationUtils.endSession();
      session = null;
    }
    return resultBinary[0];
  }
  
  public static InputStream getBinary(Content binary, String username, String password, AuthenticationDetails session){
    InputStream is = null;
    try {
      AuthenticationUtils.startSession(username, password);
      session = AuthenticationUtils.getAuthenticationDetails();
      is = ContentUtils.getContentAsInputStream(binary);
    } catch (AuthenticationFault e) {
      Logging.connectors
      .error(
          "Alfresco: Error during getting the binary for the node: "+binary.getNode().getPath()+"."
              + e.getMessage(), e);
    }
    return is;
  }
  
}
