package org.apache.manifoldcf.crawler.connectors.alfresco;

import java.rmi.RemoteException;

import org.alfresco.webservice.repository.RepositoryFault;
import org.alfresco.webservice.types.NamedValue;
import org.alfresco.webservice.types.Node;
import org.alfresco.webservice.types.Predicate;
import org.alfresco.webservice.util.AuthenticationDetails;
import org.alfresco.webservice.util.AuthenticationUtils;
import org.alfresco.webservice.util.WebServiceFactory;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.crawler.system.Logging;

public class NodeUtils {

  public static Node get(String username, String password, AuthenticationDetails session, Predicate predicate){
    Node[] resultNodes = null;
    try {
      AuthenticationUtils.startSession(username, password);
      session = AuthenticationUtils.getAuthenticationDetails();
      resultNodes = WebServiceFactory.getRepositoryService().get(predicate);
    } catch (RepositoryFault e) {
      Logging.connectors.error(
          "Alfresco: RepositoryFault during getting a node in processDocuments. Node: "
              + predicate.getNodes()[0].getPath() + ". " + e.getMessage(), e);
    } catch (RemoteException e) {
      Logging.connectors
          .error(
              "Alfresco: Remote exception error during getting a node in processDocuments. Node: "
                  + predicate.getNodes()[0].getPath() + ". " + e.getMessage(), e);
    } finally {
      AuthenticationUtils.endSession();
      session = null;
    }
    if(resultNodes!=null && resultNodes.length>0){
      return resultNodes[0];
    } else {
      return null;
    }
  }
  
  public static boolean isVersioned(String[] aspects){
    for (String aspect : aspects) {
      if(Constants.ASPECT_VERSIONABLE.equals(aspect)){
        return true;
      }
    }
    return false;
  }
  
  public static String getVersionLabel(NamedValue[] properties){
    for (NamedValue property : properties) {
      if(property.getName().equals(Constants.PROP_VERSION_LABEL)){
        return property.getValue();
      }
    }
    return StringUtils.EMPTY;
  }
  
}
