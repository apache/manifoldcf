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
package org.apache.manifoldcf.core.interfaces;

import java.util.*;

/** This interface represents parameters that get posted during UI interaction.
*/
public interface IParameterActivity
{
  public static final String _rcsid = "@(#)$Id$";

  /** Read an array of parameter values.
  *@param name is the parameter name.
  *@return the array of values, or null if it doesn't exist.
  */
  public String[] getParameterValues(String name);
  
  /** Get single parameter value.
  *@param name is the parameter name.
  *@return the value, or null if it doesn't exist.
  */
  public String getParameter(String name);
  
  /** Get a file parameter, as a binary input stream.
  *@param name is the parameter name.
  *@return the value, or null if it doesn't exist.
  */
  public BinaryInput getBinaryStream(String name)
    throws ManifoldCFException;
  
  /** Get file parameter, as a byte array.
  *@param name is the parameter name.
  *@return the binary parameter as an array of bytes.
  */
  public byte[] getBinaryBytes(String name);
  
  /** Set a parameter value.
  *@param name is the parameter name.
  *@param value is the desired value.
  */
  public void setParameter(String name, String value);
  
  /** Set an array of parameter values.
  *@param name is the parameter name.
  *@param values is the array of desired values.
  */
  public void setParameterValues(String name, String[] values);

}
