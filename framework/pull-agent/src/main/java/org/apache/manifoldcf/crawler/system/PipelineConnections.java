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

import java.util.*;

/** Pipeline connections implementation.
*/
public class PipelineConnections implements IPipelineConnections
{
  protected final IPipelineSpecificationBasic spec;
  protected final String[] transformationConnectionNames;
  protected final ITransformationConnection[] transformationConnections;
  protected final String[] outputConnectionNames;
  protected final IOutputConnection[] outputConnections;
  // We need a way to get from stage index to connection index.
  // These arrays are looked up by stage index, and return the appropriate connection index.
  protected final Map<Integer,Integer> transformationConnectionLookupMap = new HashMap<Integer,Integer>();
  protected final Map<Integer,Integer> outputConnectionLookupMap = new HashMap<Integer,Integer>();
    
  public PipelineConnections(IPipelineSpecificationBasic spec, ITransformationConnectionManager transformationConnectionManager,
    IOutputConnectionManager outputConnectionManager)
    throws ManifoldCFException
  {
    this.spec = spec;
    // Now, load all the connections we'll ever need, being sure to only load one copy of each.
    // We first segregate them into unique transformation and output connections.
    int count = spec.getStageCount();
    Set<String> transformations = new HashSet<String>();
    Set<String> outputs = new HashSet<String>();
    for (int i = 0; i < count; i++)
    {
      if (spec.checkStageOutputConnection(i))
        outputs.add(spec.getStageConnectionName(i));
      else
        transformations.add(spec.getStageConnectionName(i));
    }
      
    Map<String,Integer> transformationNameMap = new HashMap<String,Integer>();
    Map<String,Integer> outputNameMap = new HashMap<String,Integer>();
    transformationConnectionNames = new String[transformations.size()];
    outputConnectionNames = new String[outputs.size()];
    int index = 0;
    for (String connectionName : transformations)
    {
      transformationConnectionNames[index] = connectionName;
      transformationNameMap.put(connectionName,new Integer(index++));
    }
    index = 0;
    for (String connectionName : outputs)
    {
      outputConnectionNames[index] = connectionName;
      outputNameMap.put(connectionName,new Integer(index++));
    }
    // Load!
    transformationConnections = transformationConnectionManager.loadMultiple(transformationConnectionNames);
    outputConnections = outputConnectionManager.loadMultiple(outputConnectionNames);
      
    for (int i = 0; i < count; i++)
    {
      Integer k;
      if (spec.checkStageOutputConnection(i))
      {
        outputConnectionLookupMap.put(new Integer(i),outputNameMap.get(spec.getStageConnectionName(i)));
      }
      else
      {
        transformationConnectionLookupMap.put(new Integer(i),transformationNameMap.get(spec.getStageConnectionName(i)));
      }
    }
  }

  /** Get a count of all stages.
  *@return the total count of all stages.
  */
  @Override
  public int getStageCount()
  {
    return spec.getStageCount();
  }
  
  /** Find children of a given pipeline stage.  Pass -1 to find the children of the root stage.
  *@param stage is the stage index to get the children of.
  *@return the pipeline stages that represent those children.
  */
  @Override
  public int[] getStageChildren(int stage)
  {
    return spec.getStageChildren(stage);
  }
  
  /** Find parent of a given pipeline stage.  Returns -1 if there's no parent (it's the root).
  *@param stage is the stage index to get the parent of.
  *@return the pipeline stage that is the parent, or -1.
  */
  @Override
  public int getStageParent(int stage)
  {
    return spec.getStageParent(stage);
  }

  /** Get the connection name for a pipeline stage.
  *@param stage is the stage to get the connection name for.
  *@return the connection name for that stage.
  */
  @Override
  public String getStageConnectionName(int stage)
  {
    return spec.getStageConnectionName(stage);
  }

  /** Check if a stage is an output stage.
  *@param stage is the stage to check.
  *@return true if the stage represents an output connection.
  */
  @Override
  public boolean checkStageOutputConnection(int stage)
  {
    return spec.checkStageOutputConnection(stage);
  }
  
  /** Return the number of output connections.
  *@return the total number of output connections in this specification.
  */
  @Override
  public int getOutputCount()
  {
    return spec.getOutputCount();
  }
  
  /** Given an output index, return the stage number for that output.
  *@param index is the output connection index.
  *@return the stage number.
  */
  @Override
  public int getOutputStage(int index)
  {
    return spec.getOutputStage(index);
  }
  
  @Override
  public String[] getTransformationConnectionNames()
  {
    return transformationConnectionNames;
  }
    
  @Override
  public ITransformationConnection[] getTransformationConnections()
  {
    return transformationConnections;
  }
    
  @Override
  public String[] getOutputConnectionNames()
  {
    return outputConnectionNames;
  }
    
  @Override
  public IOutputConnection[] getOutputConnections()
  {
    return outputConnections;
  }
    
  @Override
  public Integer getTransformationConnectionIndex(int stage)
  {
    return transformationConnectionLookupMap.get(new Integer(stage));
  }
    
  @Override
  public Integer getOutputConnectionIndex(int stage)
  {
    return outputConnectionLookupMap.get(new Integer(stage));
  }
    
}
