/* $Id: JiraConfig.java 1488537 2013-06-01 15:30:15Z kwright $ */

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

package org.apache.manifoldcf.crawler.connectors.jira;

/**
 *
 * @author andrew
 */
public class JiraConfig {

  public static final String CLIENT_ID_PARAM = "clientid";
  public static final String CLIENT_SECRET_PARAM = "clientsecret";
  public static final String JIRAURL_TOKEN_PARAM = "jiraurl";
  public static final String REPOSITORY_ID_DEFAULT_VALUE = "jira";
  public static final String JIRA_QUERY_PARAM = "jiraquery";
  public static final String JIRA_QUERY_DEFAULT = "ORDER BY createdDate Asc";

    
}
