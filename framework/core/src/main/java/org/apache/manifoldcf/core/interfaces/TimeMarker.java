/* $Id: TimeMarker.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This is a class which uniquely identifiers a time marker, for the database layer.
*/
public class TimeMarker
{
  public static final String _rcsid = "@(#)$Id: TimeMarker.java 988245 2010-08-23 18:39:35Z kwright $";

  long timeValue;

  /** Constructor.
  *@param timeValue is the time value.
  */
  public TimeMarker(long timeValue)
  {
    this.timeValue = timeValue;
  }

  public long longValue()
  {
    return timeValue;
  }
  
  public String toString()
  {
    return new Long(timeValue).toString();
  }
  
}
