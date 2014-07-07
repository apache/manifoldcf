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
    
  public PipelineSpecificationWithVersions(IPipelineSpecification pipelineSpecification,
    QueuedDocument queuedDocument)
    throws ManifoldCFException, ServiceInterruption
  {
    this.pipelineSpecification = pipelineSpecification;
    this.queuedDocument = queuedDocument;
  }
  
  /** Get pipeline specification.
  *@return the pipeline specification.
  */
  @Override
  public IPipelineSpecification getPipelineSpecification()
  {
    return pipelineSpecification;
  }

  protected DocumentIngestStatus getStatus(int index)
  {
    IPipelineSpecificationBasic basic = pipelineSpecification.getBasicPipelineSpecification();
    // MHL
    return queuedDocument.getLastIngestedStatus(basic.getStageConnectionName(basic.getOutputStage(index)),"");
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
    
  /** For a given output index, return a parameter version string.
  *@param index is the output index.
  *@return the parameter version string.
  */
  @Override
  public String getOutputParameterVersionString(int index)
  {
    DocumentIngestStatus status = getStatus(index);
    if (status == null)
      return null;
    return status.getParameterVersion();
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
