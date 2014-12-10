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

/** This interface describes a multi-output pipeline, with existing document version information from
* each output.. 
*/
public interface IPipelineSpecificationWithVersions extends IPipelineSpecification
{
  public static final String _rcsid = "@(#)$Id$";

  /** For a given output index, return a document version string.
  *@param index is the output index.
  *@return the document version string.
  */
  public String getOutputDocumentVersionString(int index);
  
  /** For a given output index, return a transformation version string.
  *@param index is the output index.
  *@return the transformation version string.
  */
  public String getOutputTransformationVersionString(int index);

  /** For a given output index, return an output version string.
  *@param index is the output index.
  *@return the output version string.
  */
  public String getOutputVersionString(int index);
  
  /** For a given output index, return an authority name string.
  *@param index is the output index.
  *@return the authority name string.
  */
  public String getAuthorityNameString(int index);
  
}
