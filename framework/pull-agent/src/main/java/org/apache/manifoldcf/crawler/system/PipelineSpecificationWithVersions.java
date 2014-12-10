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

/** Class which handles pipeline specifications, including both new versions and old versions.
*/
public class PipelineSpecificationWithVersions implements IPipelineSpecificationWithVersions
{
  protected final IPipelineSpecification pipelineSpecification;
  protected final QueuedDocument queuedDocument;
  protected final String componentIDHash;
    
  public PipelineSpecificationWithVersions(IPipelineSpecification pipelineSpecification,
    QueuedDocument queuedDocument, String componentIDHash)
  {
    this.pipelineSpecification = pipelineSpecification;
    this.queuedDocument = queuedDocument;
    this.componentIDHash = componentIDHash;
  }
  
  protected DocumentIngestStatus getStatus(int index)
  {
    DocumentIngestStatusSet set = queuedDocument.getLastIngestedStatus(pipelineSpecification.getStageConnectionName(pipelineSpecification.getOutputStage(index)));
    if (set == null)
      return null;
    return set.getComponent(componentIDHash);
  }

  /** Get a count of all stages.
  *@return the total count of all stages.
  */
  @Override
  public int getStageCount()
  {
    return pipelineSpecification.getStageCount();
  }
  
  /** Find children of a given pipeline stage.  Pass -1 to find the children of the root stage.
  *@param stage is the stage index to get the children of.
  *@return the pipeline stages that represent those children.
  */
  @Override
  public int[] getStageChildren(int stage)
  {
    return pipelineSpecification.getStageChildren(stage);
  }
  
  /** Find parent of a given pipeline stage.  Returns -1 if there's no parent (it's the root).
  *@param stage is the stage index to get the parent of.
  *@return the pipeline stage that is the parent, or -1.
  */
  @Override
  public int getStageParent(int stage)
  {
    return pipelineSpecification.getStageParent(stage);
  }

  /** Get the connection name for a pipeline stage.
  *@param stage is the stage to get the connection name for.
  *@return the connection name for that stage.
  */
  @Override
  public String getStageConnectionName(int stage)
  {
    return pipelineSpecification.getStageConnectionName(stage);
  }

  /** Check if a stage is an output stage.
  *@param stage is the stage to check.
  *@return true if the stage represents an output connection.
  */
  @Override
  public boolean checkStageOutputConnection(int stage)
  {
    return pipelineSpecification.checkStageOutputConnection(stage);
  }
  
  /** Return the number of output connections.
  *@return the total number of output connections in this specification.
  */
  @Override
  public int getOutputCount()
  {
    return pipelineSpecification.getOutputCount();
  }
  
  /** Given an output index, return the stage number for that output.
  *@param index is the output connection index.
  *@return the stage number.
  */
  @Override
  public int getOutputStage(int index)
  {
    return pipelineSpecification.getOutputStage(index);
  }

  /** Get the transformation connection names mentioned by the IPipelineSpecification
  * object. */
  @Override
  public String[] getTransformationConnectionNames()
  {
    return pipelineSpecification.getTransformationConnectionNames();
  }
  
  /** Get the transformation connection instances mentioned by the IPipelineSpecification
  * object. */
  @Override
  public ITransformationConnection[] getTransformationConnections()
  {
    return pipelineSpecification.getTransformationConnections();
  }
  
  /** Get the output connection names mentioned by the IPipelineSpecification
  * object. */
  @Override
  public String[] getOutputConnectionNames()
  {
    return pipelineSpecification.getOutputConnectionNames();
  }
  
  /** Get the output connection instances mentioned by the IPipelineSpecification
  * object. */
  @Override
  public IOutputConnection[] getOutputConnections()
  {
    return pipelineSpecification.getOutputConnections();
  }
  
  /** Get the index of the transformation connection corresponding to a
  * specific pipeline stage. */
  @Override
  public Integer getTransformationConnectionIndex(int stage)
  {
    return pipelineSpecification.getTransformationConnectionIndex(stage);
  }
  
  /** Get the index of the output connection corresponding to a
  * specific pipeline stage. */
  @Override
  public Integer getOutputConnectionIndex(int stage)
  {
    return pipelineSpecification.getOutputConnectionIndex(stage);
  }

  /** Get the description string for a pipeline stage.
  *@param stage is the stage to get the connection name for.
  *@return the description string that stage.
  */
  @Override
  public VersionContext getStageDescriptionString(int stage)
  {
    return pipelineSpecification.getStageDescriptionString(stage);
  }

  /** For a given output index, return a document version string.
  *@param index is the output index.
  *@return the document version string.
  */
  @Override
  public String getOutputDocumentVersionString(int index)
  {
    DocumentIngestStatus status = getStatus(index);
    if (status == null)
      return null;
    return status.getDocumentVersion();
  }
    

  /** For a given output index, return a transformation version string.
  *@param index is the output index.
  *@return the transformation version string.
  */
  @Override
  public String getOutputTransformationVersionString(int index)
  {
    DocumentIngestStatus status = getStatus(index);
    if (status == null)
      return null;
    return status.getTransformationVersion();
  }

  /** For a given output index, return an output version string.
  *@param index is the output index.
  *@return the output version string.
  */
  @Override
  public String getOutputVersionString(int index)
  {
    DocumentIngestStatus status = getStatus(index);
    if (status == null)
      return null;
    return status.getOutputVersion();
  }
    
  /** For a given output index, return an authority name string.
  *@param index is the output index.
  *@return the authority name string.
  */
  @Override
  public String getAuthorityNameString(int index)
  {
    DocumentIngestStatus status = getStatus(index);
    if (status == null)
      return null;
    return status.getDocumentAuthorityNameString();
  }
}
