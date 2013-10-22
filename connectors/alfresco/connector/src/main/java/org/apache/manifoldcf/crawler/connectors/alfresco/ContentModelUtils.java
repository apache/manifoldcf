/* $Id$ */

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
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
  public static boolean isFolder(String endpoint, String username, String password, int socketTimeout, AuthenticationDetails session, Reference node) throws ManifoldCFException {
    QueryResult queryResult = null;
    try {
      WebServiceFactory.setEndpointAddress(endpoint);
      WebServiceFactory.setTimeoutMilliseconds(socketTimeout);
      AuthenticationUtils.startSession(username, password);
      session = AuthenticationUtils.getAuthenticationDetails();
      queryResult = WebServiceFactory.getRepositoryService().queryChildren(node);
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
      AuthenticationUtils.endSession();
    } catch (RepositoryFault e) {
      Logging.connectors.warn(
          "Alfresco: Repository Error during the queryChildren: "
              + e.getMessage(), e);
      ContentModelUtils.handleRepositoryFaultException(e);
    } catch (RemoteException e) {
      Logging.connectors.warn(
          "Alfresco: Remote Error during the queryChildren: "
              + e.getMessage(), e);
      ContentModelUtils.handleRemoteException(e);
    } finally {
      session = null;
    }
    return false;
  }
  
  public static void handleRepositoryFaultException(RepositoryFault e) 
      throws ManifoldCFException {
    throw new ManifoldCFException(
        "Alfresco: Error during getting children: "+e.getMessage(),e);
  }
  
  public static void handleRemoteException(RemoteException e) 
      throws ManifoldCFException {
    throw new ManifoldCFException(
        "Alfresco: Error during getting children: "+e.getMessage(),e);
  }
  
}