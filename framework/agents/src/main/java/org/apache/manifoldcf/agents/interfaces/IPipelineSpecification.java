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

/** This interface describes a multi-output pipeline.  Each stage of the pipeline is
* given a rank number, and dependencies between stages refer to that rank number.
*/
public interface IPipelineSpecification
{
  public static final String _rcsid = "@(#)$Id$";

  /** Find children of a given pipeline stage.  Pass -1 to find the children of the root stage.
  *@param stage is the stage index to get the children of.
  *@return the pipeline stages that represent those children.
  */
  public int[] getStageChildren(int stage);
  
  /** Get the connection name for a pipeline stage.
  *@param stage is the stage to get the connection name for.
  *@return the connection name for that stage.
  */
  public String getStageConnectionName(int stage);

  /** Get the description string for a pipeline stage.
  *@param stage is the stage to get the connection name for.
  *@return the description string that stage.
  */
  public String getStageDescriptionString(int stage);
  
  /** Check if a stage is an output stage.
  *@param stage is the stage to check.
  *@return true if the stage represents an output connection.
  */
  public boolean checkStageOutputConnection(int stage);
  
}
