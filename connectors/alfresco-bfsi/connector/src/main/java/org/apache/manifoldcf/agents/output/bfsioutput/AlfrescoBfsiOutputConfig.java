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
package org.apache.manifoldcf.agents.output.bfsioutput;

import org.apache.commons.lang.StringUtils;

/**
 * Parameters data for the Alfresco Bulk File System output connector.
*/
public class AlfrescoBfsiOutputConfig {
  
  /** DropFolder */
  public static final String DROPFOLDER_PARAM = "dropfolder";

  /** Create Timestamp Tree **/
  public static final String CREATE_TIMESTAMP_TREE_PARAM = "createTimestampTree";

  /** Username */
  public static final String USERNAME_PARAM = "username";
  /** Password */
  public static final String PASSWORD_PARAM = "password";
  /** Protocol */
  public static final String PROTOCOL_PARAM = "protocol";
  /** Server name */
  public static final String SERVER_PARAM = "server";
  /** Port */
  public static final String PORT_PARAM = "port";

  /** Remote Path in the context of remote drop (streaming) */
  public static final String PATH_PARAM = "path";


  public static final String CREATE_TIMESTAMP_TREE_DEFAULT_VALUE = Boolean.FALSE.toString();


  //default values
  public static final String USERNAME_DEFAULT_VALUE = "admin";
  public static final String PASSWORD_DEFAULT_VALUE = "admin";
  public static final String PROTOCOL_DEFAULT_VALUE = "http";
  public static final String SERVER_DEFAULT_VALUE = "localhost";
  public static final String PORT_DEFAULT_VALUE = "8080";
  public static final String PATH_DEFAULT_VALUE = "/user/tests";
  //default values
  public static final String DROPFOLDER_DEFAULT_VALUE = "/opt/alfresco/alf_data/contentstore/manifoldexport";






}