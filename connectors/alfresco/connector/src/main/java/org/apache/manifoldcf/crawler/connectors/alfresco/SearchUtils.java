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
import java.util.ArrayList;
import java.util.List;

import org.alfresco.webservice.repository.QueryResult;
import org.alfresco.webservice.repository.RepositoryFault;
import org.alfresco.webservice.types.NamedValue;
import org.alfresco.webservice.types.Query;
import org.alfresco.webservice.types.Reference;
import org.alfresco.webservice.types.ResultSet;
import org.alfresco.webservice.types.ResultSetRow;
import org.alfresco.webservice.types.Store;
import org.alfresco.webservice.util.AuthenticationDetails;
import org.alfresco.webservice.util.AuthenticationUtils;
import org.alfresco.webservice.util.Constants;
import org.alfresco.webservice.util.WebServiceFactory;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.crawler.system.Logging;

public class SearchUtils {
  
  private static final String SPACES_STORE = "SpacesStore";
  private static final String XPATH_COMPANY_HOME = "/app:company_home";

  public static final Store STORE = new Store(Constants.WORKSPACE_STORE,
      SPACES_STORE);
  
  private static final String PATH_PROPERTY = "{http://www.alfresco.org/model/content/1.0}path";
  
  private static final String[] EXCLUDED_PATHS = new String[]{
    "{http://www.alfresco.org/model/application/1.0}dictionary",
    "{http://www.alfresco.org/model/application/1.0}guest_home",
    "{http://www.alfresco.org/model/application/1.0}user_homes",
    "{http://www.alfresco.org/model/site/1.0}sites"};
  

  public static QueryResult luceneSearch(String endpoint, String username, String password, int socketTimeout, AuthenticationDetails session, String luceneQuery) throws IOException {
    QueryResult queryResult = null;
    Query query = new Query(Constants.QUERY_LANG_LUCENE, luceneQuery);
    try {
      WebServiceFactory.setEndpointAddress(endpoint);
      WebServiceFactory.setTimeoutMilliseconds(socketTimeout);
      AuthenticationUtils.startSession(username, password);
      session = AuthenticationUtils.getAuthenticationDetails();
      queryResult = WebServiceFactory.getRepositoryService().query(STORE, query, false);
      AuthenticationUtils.endSession();
    } catch (RepositoryFault e) {
      Logging.connectors.error(
          "Alfresco: Repository fault during addSeedDocuments: "
              + e.getMessage(), e);
      throw new IOException("Alfresco: Repository fault during addSeedDocuments: "
          + e.getMessage(), e);
    } catch (RemoteException e) {
      Logging.connectors.error(
          "Alfresco: Remote exception during addSeedDocuments: "
              + e.getMessage(), e);
      throw e;
    } finally {
      session = null;
    }
    return queryResult;
  }
  
  public static QueryResult getChildren(String endpoint, String username, String password, int socketTimeout, AuthenticationDetails session, Reference reference) throws IOException {
    QueryResult queryResult = null;
    try {
      WebServiceFactory.setEndpointAddress(endpoint);
      WebServiceFactory.setTimeoutMilliseconds(socketTimeout);
      AuthenticationUtils.startSession(username, password);
      session = AuthenticationUtils.getAuthenticationDetails();  
      queryResult = WebServiceFactory.getRepositoryService().queryChildren(reference);
      AuthenticationUtils.endSession();
    } catch (RepositoryFault e) {
      Logging.connectors.error(
          "Alfresco: RepositoryFault during getting a node in processDocuments. Node: "
              + reference.getPath() + ". " + e.getMessage(), e);
      throw new IOException("Alfresco: RepositoryFault during getting a node in processDocuments. Node: "
              + reference.getPath() + ". " + e.getMessage(), e);
      
    } catch (RemoteException e) {
      Logging.connectors
          .error(
              "Alfresco: Remote exception error during getting a node in processDocuments. Node: "
                  + reference.getPath() + ". " + e.getMessage(), e);
      throw e;
    } finally {
      session = null;
    }
    return queryResult;
  }
  
  /**
   * 
   * @param username
   * @param password
   * @param session
   * @return filtered children of the Company Home without all the special spaces
   */
  public static QueryResult getChildrenFromCompanyHome(String endpoint, String username, String password, int socketTimeout, AuthenticationDetails session) throws IOException {
    Reference companyHome = new Reference(STORE, null, XPATH_COMPANY_HOME);
    QueryResult queryResult = SearchUtils.getChildren(endpoint, username, password, socketTimeout, session, companyHome);
    ResultSet rs = queryResult.getResultSet();
    ResultSetRow[] rows = rs.getRows();
    List<ResultSetRow> filteredRows = new ArrayList<ResultSetRow>();
    for (ResultSetRow row : rows) {
      boolean hasFilteredPath = false;
      NamedValue[] properties = row.getColumns();
      String path = PropertiesUtils.getPropertyValues(properties, PATH_PROPERTY)[0];
      for(String excludedPath : EXCLUDED_PATHS){
        if(StringUtils.contains(path, excludedPath)){
          hasFilteredPath = true;
          break;
        }
      }
      if(!hasFilteredPath){
        filteredRows.add(row);
      }
    }
    ResultSetRow[] finalFilteredRows = new ResultSetRow[filteredRows.size()];
    for(int i=0; i<finalFilteredRows.length; i++){
      finalFilteredRows[i] = filteredRows.get(i);
    }
    rs.setRows(finalFilteredRows);
    return queryResult;
  }
  
}
