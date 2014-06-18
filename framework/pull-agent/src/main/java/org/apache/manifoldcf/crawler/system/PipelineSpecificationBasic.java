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
  protected final IJobDescription job;
  
  // This is offset by 1, so the first element corresponds to -1
  protected final int[][] pipelineStageChildren;
  
  protected final int[] outputs;
  
  public PipelineSpecificationBasic(IJobDescription job)
  {
    this.job = job;
    this.pipelineStageChildren = new int[job.countPipelineStages()+1][];
    int[] childrenCount = new int[pipelineStageChildren.length];
    int outputCount = 0;
    for (int i = 0; i < job.countPipelineStages(); i++)
    {
      int prerequisite = job.getPipelineStagePrerequisite(i);
      childrenCount[prerequisite+1] ++;
      if (job.getPipelineStageIsOutputConnection(i))
        outputCount++;
    }
    for (int i = 0; i < pipelineStageChildren.length; i++)
    {
      pipelineStageChildren[i] = new int[childrenCount[i]];
      childrenCount[i] = 0;
    }
    outputs = new int[outputCount];
    outputCount = 0;
    for (int i = 0; i < job.countPipelineStages(); i++)
    {
      int prerequisite = job.getPipelineStagePrerequisite(i);
      (pipelineStageChildren[prerequisite+1])[childrenCount[prerequisite+1]++] = i;
      if (job.getPipelineStageIsOutputConnection(i))
        outputs[outputCount++] = i;
    }
  }
    
  /** Get a count of all stages.
  *@return the total count of all stages.
  */
  @Override
  public int getStageCount()
  {
    return job.countPipelineStages();
  }

  /** Find children of a given pipeline stage.  Pass -1 to find the children of the root stage.
  *@param stage is the stage index to get the children of.
  *@return the pipeline stages that represent those children.
  */
  @Override
  public int[] getStageChildren(int stage)
  {
    return pipelineStageChildren[stage+1];
  }
    
  /** Find parent of a given pipeline stage.  Returns -1 if there's no parent (it's the root).
  *@param stage is the stage index to get the parent of.
  *@return the pipeline stage that is the parent, or -1.
  */
  public int getStageParent(int stage)
  {
    return job.getPipelineStagePrerequisite(stage);
  }

  /** Get the connection name for a pipeline stage.
  *@param stage is the stage to get the connection name for.
  *@return the connection name for that stage.
  */
  @Override
  public String getStageConnectionName(int stage)
  {
    return job.getPipelineStageConnectionName(stage);
  }
    
  /** Check if a stage is an output stage.
  *@param stage is the stage to check.
  *@return true if the stage represents an output connection.
  */
  @Override
  public boolean checkStageOutputConnection(int stage)
  {
    return job.getPipelineStageIsOutputConnection(stage);
  }

  /** Return the number of output connections.
  *@return the total number of output connections in this specification.
  */
  public int getOutputCount()
  {
    return outputs.length;
  }
    
  /** Given an output index, return the stage number for that output.
  *@param index is the output connection index.
  *@return the stage number.
  */
  public int getOutputStage(int index)
  {
    return outputs[index];
  }

}
