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
package org.apache.manifoldcf.agents.interfaces;

import org.apache.manifoldcf.core.interfaces.*;

/** This interface describes a multi-output pipeline, where each stage has an already-computed
* description string.
*/
public interface IPipelineSpecification extends IPipelineConnections
{
  public static final String _rcsid = "@(#)$Id$";

  /** Get the description string for a pipeline stage.
  *@param stage is the stage to get the connection name for.
  *@return the description string that stage.
  */
  public VersionContext getStageDescriptionString(int stage);
  
}
