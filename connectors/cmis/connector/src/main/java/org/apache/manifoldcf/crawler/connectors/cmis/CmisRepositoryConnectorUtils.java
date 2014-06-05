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
import java.util.StringTokenizer;

import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.bindings.spi.atompub.AbstractAtomPubService;
import org.apache.chemistry.opencmis.client.bindings.spi.atompub.AtomPubParser;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.crawler.system.Logging;

/**
 * 
 * @author Piergiorgio Lucidi
 *
 */
public class CmisRepositoryConnectorUtils {

  private static final String LOAD_LINK_METHOD_NAME = "loadLink";
  private static final String FROM_TOKEN = "from";
  private static final String SEP = " ";
  private static final String SELECT_STAR_CLAUSE = "select *";
  private static final String OBJECT_ID_TERM = "cmis:objectId,";
  
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
  
  /**
   * Utility method to consider the objectId whenever it is not present in the select clause
   * @param cmisQuery
   * @return the cmisQuery with the cmis:objectId property added in the select clause
   */
  public static String getCmisQueryWithObjectId(String cmisQuery){
    String cmisQueryResult = StringUtils.EMPTY;
    StringTokenizer cmisQueryTokenized = new StringTokenizer(cmisQuery.trim());
    String selectClause = StringUtils.EMPTY;
    boolean firstTerm = true;
    while(cmisQueryTokenized.hasMoreElements()){
        String term = cmisQueryTokenized.nextToken();
        if(!term.equalsIgnoreCase(FROM_TOKEN)){
          if(firstTerm){
            selectClause+=term;
            firstTerm = false;
          } else {
            selectClause+=SEP+term;
          }
          
        } else {
          break;
        }
    }
    
    if(selectClause.equalsIgnoreCase(SELECT_STAR_CLAUSE)){
      cmisQueryResult = cmisQuery;
    } else {
      //get the second term and add the cmis:objectId term
      StringTokenizer selectClauseTokenized = new StringTokenizer(selectClause.trim());
      boolean firstTermSelectClause = true;
      String secondTerm = StringUtils.EMPTY;
      while(selectClauseTokenized.hasMoreElements()){
          String term = selectClauseTokenized.nextToken();
          if(firstTermSelectClause){
            firstTermSelectClause = false;
          } else if(!firstTermSelectClause){
            //this is the second term
            secondTerm = term;
            break;
          }
      }
      cmisQueryResult = StringUtils.replaceOnce(cmisQuery, secondTerm, OBJECT_ID_TERM + secondTerm);
    }
    
    return cmisQueryResult;
  }
  
}