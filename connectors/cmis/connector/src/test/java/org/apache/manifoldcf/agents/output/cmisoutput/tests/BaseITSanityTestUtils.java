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
package org.apache.manifoldcf.agents.output.cmisoutput.tests;

import org.apache.commons.lang.StringUtils;

/**
 * Utility class dedicated to integration tests for the CMIS Output Connector
 * @author piergiorgiolucidi
 *
 */
public class BaseITSanityTestUtils {

	public static final String REPLACER = "?";

	public static final String CMIS_TEST_QUERY_CHANGE_DOC = 
  		"SELECT * FROM cmis:document WHERE cmis:name='"+BaseITSanityTestUtils.REPLACER+"'";
	
	public static final String CMIS_TEST_QUERY_TARGET_REPO_ALL = "SELECT * FROM cmis:document WHERE CONTAINS('testdata')";
  
	
  //Test values for the source repository
  public static final String SOURCE_USERNAME_VALUE = "dummyuser";
  public static final String SOURCE_PASSWORD_VALUE = "dummysecrect";
  public static final String SOURCE_PROTOCOL_VALUE = "http";
  public static final String SOURCE_SERVER_VALUE = "localhost";
  public static final String SOURCE_PORT_VALUE = "9091";
  public static final String SOURCE_BINDING_VALUE = "atom";
  public static final String SOURCE_PATH_VALUE = "/chemistry-opencmis-server-inmemory/atom";
  public static final String SOURCE_REPOSITORY_ID_VALUE = StringUtils.EMPTY;
  
  //Test values for the source repository
  public static final String TARGET_USERNAME_VALUE = "dummyuser";
  public static final String TARGET_PASSWORD_VALUE = "dummysecrect";
  public static final String TARGET_PROTOCOL_VALUE = "http";
  public static final String TARGET_SERVER_VALUE = "localhost";
  public static final String TARGET_PORT_VALUE = "9092";
  public static final String TARGET_BINDING_VALUE = "atom";
  public static final String TARGET_PATH_VALUE = "/chemistry-opencmis-server-inmemory/atom";
  public static final String TARGET_REPOSITORY_ID_VALUE = StringUtils.EMPTY;
	
}
