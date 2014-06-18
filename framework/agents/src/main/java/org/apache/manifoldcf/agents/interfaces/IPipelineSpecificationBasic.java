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
public interface IPipelineSpecificationBasic
{
  public static final String _rcsid = "@(#)$Id$";

  /** Get a count of all stages.
  *@return the total count of all stages.
  */
  public int getStageCount();
  
  /** Find children of a given pipeline stage.  Pass -1 to find the children of the root stage.
  *@param stage is the stage index to get the children of.
  *@return the pipeline stages that represent those children.
  */
  public int[] getStageChildren(int stage);
  
  /** Find parent of a given pipeline stage.  Returns -1 if there's no parent (it's the root).
  *@param stage is the stage index to get the parent of.
  *@return the pipeline stage that is the parent, or -1.
  */
  public int getStageParent(int stage);

  /** Get the connection name for a pipeline stage.
  *@param stage is the stage to get the connection name for.
  *@return the connection name for that stage.
  */
  public String getStageConnectionName(int stage);

  /** Check if a stage is an output stage.
  *@param stage is the stage to check.
  *@return true if the stage represents an output connection.
  */
  public boolean checkStageOutputConnection(int stage);
  
  // This part of the interface describes the output connections within.  They
  // are intrinsically ordered independently of stages.  It is presumed that
  // no single output connection appears more than once.
  
  /** Return the number of output connections.
  *@return the total number of output connections in this specification.
  */
  public int getOutputCount();
  
  /** Given an output index, return the stage number for that output.
  *@param index is the output connection index.
  *@return the stage number.
  */
  public int getOutputStage(int index);
  
}
