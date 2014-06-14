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
  protected final IPipelineSpecificationBasic basicSpecification;
  protected final String[] pipelineDescriptionStrings;
    
  public PipelineSpecification(IPipelineSpecificationBasic basicSpecification, IJobDescription job, IIncrementalIngester ingester)
    throws ManifoldCFException, ServiceInterruption
  {
    this.basicSpecification = basicSpecification;
    this.pipelineDescriptionStrings = new String[basicSpecification.getStageCount()];
    for (int i = 0; i < pipelineDescriptionStrings.length; i++)
    {
      // Note: this needs to change when output connections become part of the pipeline
      String descriptionString;
      if (basicSpecification.checkStageOutputConnection(i))
      {
        descriptionString = ingester.getOutputDescription(basicSpecification.getStageConnectionName(i),job.getOutputSpecification());
      }
      else
      {
        descriptionString = ingester.getTransformationDescription(basicSpecification.getStageConnectionName(i),job.getPipelineStageSpecification(i));
      }
      this.pipelineDescriptionStrings[i] = descriptionString;
    }
  }

  /** Get the basic pipeline specification.
  *@return the specification.
  */
  @Override
  public IPipelineSpecificationBasic getBasicPipelineSpecification()
  {
    return basicSpecification;
  }

  /** Get the description string for a pipeline stage.
  *@param stage is the stage to get the connection name for.
  *@return the description string that stage.
  */
  @Override
  public String getStageDescriptionString(int stage)
  {
    return pipelineDescriptionStrings[stage];
  }
}
