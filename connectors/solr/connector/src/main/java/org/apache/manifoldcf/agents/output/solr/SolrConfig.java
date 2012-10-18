/* $Id: SolrConfig.java 991374 2010-08-31 22:32:08Z kwright $ */

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
package org.apache.manifoldcf.agents.output.solr;


/** Parameters and output data for SOLR output connector.
*/
public class SolrConfig
{
  public static final String _rcsid = "@(#)$Id: SolrConfig.java 991374 2010-08-31 22:32:08Z kwright $";

  // Configuration parameters

  /** Protocol */
  public static final String PARAM_PROTOCOL = "Server protocol";
  /** Server name */
  public static final String PARAM_SERVER = "Server name";
  /** Port */
  public static final String PARAM_PORT = "Server port";
  /** Webapp */
  public static final String PARAM_WEBAPPNAME = "Server web application";
  /** Core */
  public static final String PARAM_CORE = "Solr core name";
  /** Update path */
  public static final String PARAM_UPDATEPATH = "Server update handler";
  /** Remove path */
  public static final String PARAM_REMOVEPATH = "Server remove handler";
  /** Status path */
  public static final String PARAM_STATUSPATH = "Server status handler";
  /** Id field */
  public static final String PARAM_IDFIELD = "Solr id field name";
  /** Optional basic auth realm */
  public static final String PARAM_REALM = "Realm";
  /** Optional user ID */
  public static final String PARAM_USERID = "User ID";
  /** Optional user password */
  public static final String PARAM_PASSWORD = "Password";
  /** Enable commits */
  public static final String PARAM_COMMITS = "Commits";
  /** Commit within time */
  public static final String PARAM_COMMITWITHIN = "Commit within";
  /** Keystore */
  public static final String PARAM_KEYSTORE = "Keystore";
  /** Maximum document length */
  public static final String PARAM_MAXLENGTH = "Maximum document length";
  /** Included mime types */
  public static final String PARAM_INCLUDEDMIMETYPES = "Included mime types";
  /** Excluded mime types */
  public static final String PARAM_EXCLUDEDMIMETYPES="Excluded mime types";
  /** Node describing an argument */
  public static final String NODE_ARGUMENT = "argument";
  /** Attribute with the argument name */
  public static final String ATTRIBUTE_NAME = "name";
  /** Attribute with the argument value */
  public static final String ATTRIBUTE_VALUE = "value";
  
  // Output specification

  /** Node describing a fieldmap */
  public static final String NODE_FIELDMAP = "fieldmap";
  /** Attribute describing a source field name */
  public static final String ATTRIBUTE_SOURCE = "source";
  /** Attribute describing a target field name */
  public static final String ATTRIBUTE_TARGET = "target";

  /** Node describing a forcedfield */
  public static final String NODE_FORCEDFIELD = "forcedfield";
  /** Attribute describing a field name */
  public static final String ATTRIBUTE_FORCEDNAME = "name";
  /** Attribute describing a field value */
  public static final String ATTRIBUTE_FORCEDVALUE = "value";
}
