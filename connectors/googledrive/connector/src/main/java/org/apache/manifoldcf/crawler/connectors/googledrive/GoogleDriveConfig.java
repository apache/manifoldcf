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

package org.apache.manifoldcf.crawler.connectors.googledrive;

/**
 *
 * @author andrew
 */
public class GoogleDriveConfig {

  public static final String CLIENT_ID_PARAM = "clientid";
  public static final String CLIENT_SECRET_PARAM = "clientsecret";
  public static final String REFRESH_TOKEN_PARAM = "refreshtoken";
  public static final String REPOSITORY_ID_DEFAULT_VALUE = "googledrive";
  public static final String GOOGLEDRIVE_QUERY_PARAM = "googledriveQuery";
  public static final String GOOGLEDRIVE_QUERY_DEFAULT = "mimeType='application/vnd.google-apps.folder' and trashed=false";
}
