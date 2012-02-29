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
package org.apache.manifoldcf.crawler.connectors.cmis;

import java.lang.reflect.Method;

import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.bindings.spi.atompub.AbstractAtomPubService;
import org.apache.chemistry.opencmis.client.bindings.spi.atompub.AtomPubParser;
import org.apache.manifoldcf.crawler.system.Logging;

/**
 * 
 * @author Piergiorgio Lucidi
 *
 */
public class CmisRepositoryConnectorUtils {

  private static final String LOAD_LINK_METHOD_NAME = "loadLink";
  
  public static final String getDocumentURL(final Document document, final Session session) {
    String link = null;
    try {
        Method loadLink = AbstractAtomPubService.class.getDeclaredMethod(LOAD_LINK_METHOD_NAME, 
            new Class[] { String.class, String.class, String.class, String.class });
        
        loadLink.setAccessible(true);
        
        link = (String) loadLink.invoke(session.getBinding().getObjectService(), session.getRepositoryInfo().getId(),
            document.getId(), AtomPubParser.LINK_REL_CONTENT, null);
    } catch (Exception e) {
      Logging.connectors.error(
          "CMIS: Error during getting the content stream url: "
              + e.getMessage(), e);
    }
    return link;
  }
  
}