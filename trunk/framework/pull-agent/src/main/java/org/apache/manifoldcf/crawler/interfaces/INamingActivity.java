/* $Id: INamingActivity.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

/** This interface abstracts from the activities that use global, connection-specific, and job-specific names.
*
*/
public interface INamingActivity
{
  public static final String _rcsid = "@(#)$Id: INamingActivity.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Create a global string from a simple string.
  *@param simpleString is the simple string.
  *@return a global string.
  */
  public String createGlobalString(String simpleString);

  /** Create a connection-specific string from a simple string.
  *@param simpleString is the simple string.
  *@return a connection-specific string.
  */
  public String createConnectionSpecificString(String simpleString);

  /** Create a job-based string from a simple string.
  *@param simpleString is the simple string.
  *@return a job-specific string.
  */
  public String createJobSpecificString(String simpleString);

}
