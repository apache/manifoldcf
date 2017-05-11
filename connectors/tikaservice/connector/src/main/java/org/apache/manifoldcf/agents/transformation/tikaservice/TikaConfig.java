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

package org.apache.manifoldcf.agents.transformation.tikaservice;

/** Parameters for Tika transformation connector.
 */
public class TikaConfig {

  // Configuration parameters
  public static final String PARAM_TIKAHOSTNAME = "tikaHostname";
  public static final String PARAM_TIKAPORT = "tikaPort";
  public static final String TIKAHOSTNAME_DEFAULT = "localhost";
  public static final String TIKAPORT_DEFAULT = "9998";

  // Specification nodes and values
  public static final String NODE_FIELDMAP = "fieldmap";
  public static final String NODE_KEEPMETADATA = "keepAllMetadata";
  public static final String NODE_LOWERNAMES = "lowerNames";
  public static final String NODE_WRITELIMIT = "writeLimit";
  public static final int WRITELIMIT_DEFAULT = -1;
  public static final String NODE_IGNORETIKAEXCEPTION = "ignoreException";
  public static final String ATTRIBUTE_SOURCE = "source";
  public static final String ATTRIBUTE_TARGET = "target";
  public static final String ATTRIBUTE_VALUE = "value";
  
}
