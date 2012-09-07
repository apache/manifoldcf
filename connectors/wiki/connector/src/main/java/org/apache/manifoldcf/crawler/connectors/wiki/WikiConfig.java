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
package org.apache.manifoldcf.crawler.connectors.wiki;


/** Parameters and output data for Wiki repository connector.
*/
public class WikiConfig
{
  public static final String _rcsid = "@(#)$Id$";

  // Configuration parameters

  /** Protocol */
  public static final String PARAM_PROTOCOL = "Server protocol";
  /** Server name */
  public static final String PARAM_SERVER = "Server name";
  /** Port */
  public static final String PARAM_PORT = "Server port";
  /** Path */
  public static final String PARAM_PATH = "Server path";
	
  public static final String PARAM_LOGIN = "serverlogin";
  public static final String PARAM_PASSWORD = "serverpass";
  public static final String PARAM_DOMAIN = "serverdomain";

  // Document specification

  /** Namespace and title prefix */
  public static final String NODE_NAMESPACE_TITLE_PREFIX = "namespaceandprefix";
  /** Namespace attribute */
  public static final String ATTR_NAMESPACE = "namespace";
  /** Title prefix attribute */
  public static final String ATTR_TITLEPREFIX = "titleprefix";
}
