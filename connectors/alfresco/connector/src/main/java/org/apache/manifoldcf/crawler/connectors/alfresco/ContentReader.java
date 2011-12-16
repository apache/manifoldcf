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
