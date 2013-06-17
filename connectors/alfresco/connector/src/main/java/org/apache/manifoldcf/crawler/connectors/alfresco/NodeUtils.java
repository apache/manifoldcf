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

import java.io.IOException;
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

/**
 * 
 * This is an utility class dedicated to the Alfresco Node Web Services API  
 * @author Piergiorgio Lucidi
 *
 */
public class NodeUtils {
  
  /** the splitter character to split the node reference */
  private static final String NODE_REFERENCE_SPLIT = "/";

  /**
   * Retrieve a specific instance of a content required in the predicate object
   * 
   * @param username
   * @param password
   * @param session
   * @param predicate
   * @return the Node object instance of the current content
   */
  public static Node get(String endpoint, String username, String password, int socketTimeout, AuthenticationDetails session, Predicate predicate) throws IOException {
    Node[] resultNodes = null;
    try {
      WebServiceFactory.setEndpointAddress(endpoint);
      WebServiceFactory.setTimeoutMilliseconds(socketTimeout);
      AuthenticationUtils.startSession(username, password);
      session = AuthenticationUtils.getAuthenticationDetails();
      resultNodes = WebServiceFactory.getRepositoryService().get(predicate);
      AuthenticationUtils.endSession();
    } catch (RepositoryFault e) {
      Logging.connectors.error(
          "Alfresco: RepositoryFault during getting a node in processDocuments. Node: "
              + predicate.getNodes()[0].getPath() + ". " + e.getMessage(), e);
      throw new IOException("Alfresco: RepositoryFault during getting a node in processDocuments. Node: "
          + predicate.getNodes()[0].getPath() + ". " + e.getMessage(), e);
    } catch (RemoteException e) {
      Logging.connectors
          .error(
              "Alfresco: Remote exception error during getting a node in processDocuments. Node: "
                  + predicate.getNodes()[0].getPath() + ". " + e.getMessage(), e);
      throw e;
    } finally {
      session = null;
    }
    if(resultNodes!=null && resultNodes.length>0){
      return resultNodes[0];
    } else {
      return null;
    }
  }
  
  /**
   * 
   * @param aspects
   * @return TRUE if the current node is versioned, otherwise FALSE
   */
  public static boolean isVersioned(String[] aspects){
    for (String aspect : aspects) {
      if(Constants.ASPECT_VERSIONABLE.equals(aspect)){
        return true;
      }
    }
    return false;
  }
  
  /**
   * 
   * @param properties
   * @return the version label for the current node
   */
  public static String getVersionLabel(NamedValue[] properties){
    for (NamedValue property : properties) {
      if(property.getName().equals(Constants.PROP_VERSION_LABEL)){
        return property.getValue();
      }
    }
    return StringUtils.EMPTY;
  }
  
  /**
   * Build the UUID starting from the Alfresco node reference
   * @param nodeReference
   * @return the UUID for the current node
   */
  public static String getUuidFromNodeReference(String nodeReference){
    String uuid = StringUtils.EMPTY;
    String[] nodeReferenceSplitted = StringUtils.split(nodeReference, NODE_REFERENCE_SPLIT);
    if(nodeReferenceSplitted!=null
        && nodeReferenceSplitted.length>2
        && StringUtils.isNotEmpty(nodeReferenceSplitted[2])){
      uuid = nodeReferenceSplitted[2];
    }
    return uuid;
  }
  
}
