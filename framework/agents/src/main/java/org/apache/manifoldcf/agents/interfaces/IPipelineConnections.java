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

/** This interface caches IOutputConnection and ITransformationConnection objects
* required by an IPipelineSpecification.
*/
public interface IPipelineConnections extends IPipelineSpecificationBasic
{
  /** Get the transformation connection names mentioned by the IPipelineSpecification
  * object. */
  public String[] getTransformationConnectionNames();
  
  /** Get the transformation connection instances mentioned by the IPipelineSpecification
  * object. */
  public ITransformationConnection[] getTransformationConnections();
  
  /** Get the output connection names mentioned by the IPipelineSpecification
  * object. */
  public String[] getOutputConnectionNames();
  
  /** Get the output connection instances mentioned by the IPipelineSpecification
  * object. */
  public IOutputConnection[] getOutputConnections();
  
  /** Get the index of the transformation connection corresponding to a
  * specific pipeline stage. */
  public Integer getTransformationConnectionIndex(int stage);
  
  /** Get the index of the output connection corresponding to a
  * specific pipeline stage. */
  public Integer getOutputConnectionIndex(int stage);

}
