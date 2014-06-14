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
package org.apache.manifoldcf.crawler.system;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;

/** Basic pipeline specification implementation.  Constructed from a job description.
*/
public class PipelineSpecificationBasic implements IPipelineSpecificationBasic
{
  protected final String[] transformationConnectionNames;
  protected final String outputConnectionName;
    
  public PipelineSpecificationBasic(IJobDescription job)
  {
    transformationConnectionNames = new String[job.countPipelineStages()];
    outputConnectionName = job.getOutputConnectionName();
    for (int i = 0; i < transformationConnectionNames.length; i++)
    {
      transformationConnectionNames[i] = job.getPipelineStageConnectionName(i);
    }
  }
    
  /** Get a count of all stages.
  *@return the total count of all stages.
  */
  @Override
  public int getStageCount()
  {
    return transformationConnectionNames.length + 1;
  }

  /** Find children of a given pipeline stage.  Pass -1 to find the children of the root stage.
  *@param stage is the stage index to get the children of.
  *@return the pipeline stages that represent those children.
  */
  @Override
  public int[] getStageChildren(int stage)
  {
    if (stage < transformationConnectionNames.length + 1)
      return new int[]{stage + 1};
    return new int[0];
  }
    
  /** Find parent of a given pipeline stage.  Returns -1 if there's no parent (it's the root).
  *@param stage is the stage index to get the parent of.
  *@return the pipeline stage that is the parent, or -1.
  */
  public int getStageParent(int stage)
  {
    return stage - 1;
  }

  /** Get the connection name for a pipeline stage.
  *@param stage is the stage to get the connection name for.
  *@return the connection name for that stage.
  */
  @Override
  public String getStageConnectionName(int stage)
  {
    if (stage < transformationConnectionNames.length)
      return transformationConnectionNames[stage];
    return outputConnectionName;
  }
    
  /** Check if a stage is an output stage.
  *@param stage is the stage to check.
  *@return true if the stage represents an output connection.
  */
  @Override
  public boolean checkStageOutputConnection(int stage)
  {
    return stage == transformationConnectionNames.length;
  }

  /** Return the number of output connections.
  *@return the total number of output connections in this specification.
  */
  public int getOutputCount()
  {
    return 1;
  }
    
  /** Given an output index, return the stage number for that output.
  *@param index is the output connection index.
  *@return the stage number.
  */
  public int getOutputStage(int index)
  {
    return transformationConnectionNames.length;
  }

}
