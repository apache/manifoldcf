package org.apache.manifoldcf.crawler.connectors.alfresco;

import java.rmi.RemoteException;

import org.alfresco.webservice.repository.QueryResult;
import org.alfresco.webservice.repository.RepositoryFault;
import org.alfresco.webservice.types.NamedValue;
import org.alfresco.webservice.types.Reference;
import org.alfresco.webservice.types.ResultSet;
import org.alfresco.webservice.types.ResultSetRow;
import org.alfresco.webservice.util.AuthenticationDetails;
import org.alfresco.webservice.util.AuthenticationUtils;
import org.alfresco.webservice.util.WebServiceFactory;
import org.apache.manifoldcf.crawler.system.Logging;

public class ContentModelUtils {

  /**
   * this method checks if a node is a document: verify if exists a property named cm:content.
   * It could be improved to support any property that has a datatype d:content.
   * @param properties
   * @return TRUE if the node is a document, otherwise FALSE
   */
  public static boolean isDocument(NamedValue[] properties) {
    for (NamedValue property : properties) {
      if(property.getName().equals(Constants.PROP_CONTENT)){
        return true;
      }
    }
    return false;
  }
  
  /**
   * Check if the current node has associated children
   * @param session
   * @param node
   * @return TRUE if the reference contains a node that is an Alfresco space, otherwise FALSE
   */
  public static boolean isFolder(String username, String password, AuthenticationDetails session, Reference node){
    QueryResult queryResult = null;
    try {
      AuthenticationUtils.startSession(username, password);
      session = AuthenticationUtils.getAuthenticationDetails();
      queryResult = WebServiceFactory.getRepositoryService().queryChildren(node);
    } catch (RepositoryFault e) {
      Logging.connectors.warn(
          "Alfresco: Repository Error during the queryChildren: "
              + e.getMessage(), e);
    } catch (RemoteException e) {
      Logging.connectors.warn(
          "Alfresco: Remote Error during the queryChildren: "
              + e.getMessage(), e);
    } finally {
      AuthenticationUtils.endSession();
      session = null;
    }
    
    if(queryResult!=null){
      ResultSet rs = queryResult.getResultSet();
      if(rs!=null){
        ResultSetRow[] rows = rs.getRows();
        if(rows!=null){
          if(rows.length>0){
            return true;
          }
        }
      }
    }
    return false;
  }
  
}
