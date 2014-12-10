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

/** Class which handles pipeline specifications that include current (new) description strings.
*/
public class PipelineSpecification implements IPipelineSpecification
{
  protected final IPipelineConnections connections;
  protected final VersionContext[] pipelineDescriptionStrings;
    
  public PipelineSpecification(IPipelineConnections connections, IJobDescription job, IIncrementalIngester ingester)
    throws ManifoldCFException, ServiceInterruption
  {
    this.connections = connections;
    this.pipelineDescriptionStrings = new VersionContext[connections.getStageCount()];
    for (int i = 0; i < pipelineDescriptionStrings.length; i++)
    {
      // Note: this needs to change when output connections become part of the pipeline
      VersionContext descriptionString;
      if (connections.checkStageOutputConnection(i))
      {
        descriptionString = ingester.getOutputDescription(connections.getOutputConnections()[connections.getOutputConnectionIndex(i).intValue()],job.getPipelineStageSpecification(i));
      }
      else
      {
        descriptionString = ingester.getTransformationDescription(connections.getTransformationConnections()[connections.getTransformationConnectionIndex(i).intValue()],job.getPipelineStageSpecification(i));
      }
      this.pipelineDescriptionStrings[i] = descriptionString;
    }
  }

  /** Get a count of all stages.
  *@return the total count of all stages.
  */
  @Override
  public int getStageCount()
  {
    return connections.getStageCount();
  }
  
  /** Find children of a given pipeline stage.  Pass -1 to find the children of the root stage.
  *@param stage is the stage index to get the children of.
  *@return the pipeline stages that represent those children.
  */
  @Override
  public int[] getStageChildren(int stage)
  {
    return connections.getStageChildren(stage);
  }
  
  /** Find parent of a given pipeline stage.  Returns -1 if there's no parent (it's the root).
  *@param stage is the stage index to get the parent of.
  *@return the pipeline stage that is the parent, or -1.
  */
  @Override
  public int getStageParent(int stage)
  {
    return connections.getStageParent(stage);
  }

  /** Get the connection name for a pipeline stage.
  *@param stage is the stage to get the connection name for.
  *@return the connection name for that stage.
  */
  @Override
  public String getStageConnectionName(int stage)
  {
    return connections.getStageConnectionName(stage);
  }

  /** Check if a stage is an output stage.
  *@param stage is the stage to check.
  *@return true if the stage represents an output connection.
  */
  @Override
  public boolean checkStageOutputConnection(int stage)
  {
    return connections.checkStageOutputConnection(stage);
  }
  
  /** Return the number of output connections.
  *@return the total number of output connections in this specification.
  */
  @Override
  public int getOutputCount()
  {
    return connections.getOutputCount();
  }
  
  /** Given an output index, return the stage number for that output.
  *@param index is the output connection index.
  *@return the stage number.
  */
  @Override
  public int getOutputStage(int index)
  {
    return connections.getOutputStage(index);
  }

  /** Get the transformation connection names mentioned by the IPipelineSpecification
  * object. */
  @Override
  public String[] getTransformationConnectionNames()
  {
    return connections.getTransformationConnectionNames();
  }
  
  /** Get the transformation connection instances mentioned by the IPipelineSpecification
  * object. */
  @Override
  public ITransformationConnection[] getTransformationConnections()
  {
    return connections.getTransformationConnections();
  }
  
  /** Get the output connection names mentioned by the IPipelineSpecification
  * object. */
  @Override
  public String[] getOutputConnectionNames()
  {
    return connections.getOutputConnectionNames();
  }
  
  /** Get the output connection instances mentioned by the IPipelineSpecification
  * object. */
  @Override
  public IOutputConnection[] getOutputConnections()
  {
    return connections.getOutputConnections();
  }
  
  /** Get the index of the transformation connection corresponding to a
  * specific pipeline stage. */
  @Override
  public Integer getTransformationConnectionIndex(int stage)
  {
    return connections.getTransformationConnectionIndex(stage);
  }
  
  /** Get the index of the output connection corresponding to a
  * specific pipeline stage. */
  @Override
  public Integer getOutputConnectionIndex(int stage)
  {
    return connections.getOutputConnectionIndex(stage);
  }

  /** Get the description string for a pipeline stage.
  *@param stage is the stage to get the connection name for.
  *@return the description string that stage.
  */
  @Override
  public VersionContext getStageDescriptionString(int stage)
  {
    return pipelineDescriptionStrings[stage];
  }
}
