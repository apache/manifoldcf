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

package org.apache.manifoldcf.agents.transformation.tikaservice.rmeta;

/**
 * Parameters for Tika transformation connector.
 */
public class TikaConfig {

  // Configuration parameters
  public static final String PARAM_TIKAHOSTNAME = "tikaHostname";
  public static final String PARAM_TIKAPORT = "tikaPort";
  public static final String PARAM_CONNECTIONTIMEOUT = "connectionTimeout";
  public static final String PARAM_SOCKETTIMEOUT = "socketTimeout";
  public static final String PARAM_RETRYINTERVAL = "retryInterval";
  public static final String PARAM_RETRYINTERVALTIKADOWN = "retryIntervalTikaDown";
  public static final String PARAM_RETRYNUMBER = "retryNumber";
  public static final String TIKAHOSTNAME_DEFAULT = "localhost";
  public static final String TIKAPORT_DEFAULT = "9998";
  public static final String CONNECTIONTIMEOUT_DEFAULT = "60000";
  public static final String SOCKETTIMEOUT_DEFAULT = "60000";
  public static final String RETRYINTERVAL_DEFAULT = "20000";
  public static final String RETRYINTERVALTIKADOWN_DEFAULT = "120000";
  public static final String RETRYNUMBER_DEFAULT = "1";

  // Specification nodes and values
  public static final String NODE_FIELDMAP = "fieldmap";
  public static final String NODE_KEEPMETADATA = "keepAllMetadata";
  public static final String NODE_LOWERNAMES = "lowerNames";
  public static final String NODE_WRITELIMIT = "writeLimit";
  public static final String NODE_EXTRACTARCHIVES = "extractArchives";
  public static final String NODE_MAXEMBEDDEDRESOURCES = "maxEmbeddedResources";
  public static final String NODE_MAXMETADATAVALUELENGTH = "maxMetadataValueLength";
  public static final String NODE_TOTALMETADATALIMIT = "totalMetadataLimit";
  public static final String MAXMETADATAVALUELENGTH_DEFAULT = "250000";
  public static final String TOTALMETADATALIMIT_DEFAULT = "500000";
  public static final int WRITELIMIT_DEFAULT = -1;
  public static final int MAXEMBEDDEDRESOURCES_DEFAULT = -1;
  public static final String NODE_IGNORETIKAEXCEPTION = "ignoreException";
  public static final String ATTRIBUTE_SOURCE = "source";
  public static final String ATTRIBUTE_TARGET = "target";
  public static final String ATTRIBUTE_VALUE = "value";

}
