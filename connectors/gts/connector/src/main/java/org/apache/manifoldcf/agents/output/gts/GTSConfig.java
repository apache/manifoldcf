/* $Id: GTSConfig.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.agents.output.gts;


/** Parameters and output data for MetaCarta GTS output connector.
*/
public class GTSConfig
{
  public static final String _rcsid = "@(#)$Id: GTSConfig.java 988245 2010-08-23 18:39:35Z kwright $";

  // Configuration parameters

  /** Ingest URI */
  public static final String PARAM_INGESTURI = "Ingestion URI";
  /** Optional realm */
  public static final String PARAM_REALM = "Realm";
  /** Optional user ID */
  public static final String PARAM_USERID = "User ID";
  /** Optional user password */
  public static final String PARAM_PASSWORD = "Password";

  // Output specification

  /** Collection node */
  public static final String NODE_COLLECTION = "collection";
  /** Document template node */
  public static final String NODE_DOCUMENTTEMPLATE = "documenttemplate";
  /** Name attribute */
  public static final String ATTRIBUTE_VALUE = "value";

}
