/* $Id: FileOutputConfig.java 1299512 2013-05-31 22:59:38Z minoru $ */

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

package org.apache.manifoldcf.agents.output.filesystem;

import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IPostParameters;

import java.util.Locale;


public class FileOutputConfig extends FileOutputParam {

  /**
   * 
   */
  private static final long serialVersionUID = -2071290103498352538L;

  /** Parameters used for the configuration */
  final private static ParameterEnum[] CONFIGURATIONLIST = {};

  /** Build a set of ElasticSearchParameters by reading ConfigParams. If the
   * value returned by ConfigParams.getParameter is null, the default value is
   * set.
   * 
   * @param params
   */
  public FileOutputConfig(ConfigParams params)
  {
    super(CONFIGURATIONLIST);
    for (ParameterEnum param : CONFIGURATIONLIST) {
      String value = params.getParameter(param.name());
      if (value == null) {
        value = param.defaultValue;
      }
      put(param, value);
    }
  }

  /**
   * @param variableContext
   * @param parameters
   */
  public final static void contextToConfig(IPostParameters variableContext, ConfigParams parameters) {
    for (ParameterEnum param : CONFIGURATIONLIST) {
      String p = variableContext.getParameter(param.name().toLowerCase(Locale.ROOT));
      if (p != null) {
        parameters.setParameter(param.name(), p);
      }
    }
  }

}
