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
package org.apache.manifoldcf.authorities.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;

/** This interface describes a paper object which is an authority group.
*/
public interface IAuthorityGroup
{
  /** Set 'isnew' condition.
  *@param isnew true if this is a new instance.
  */
  public void setIsNew(boolean isnew);
  
  /** Get 'isnew' condition.
  *@return true if this is a new connection, false otherwise.
  */
  public boolean getIsNew();

  /** Set name.
  *@param name is the name.
  */
  public void setName(String name);

  /** Get name.
  *@return the name
  */
  public String getName();

  /** Set description.
  *@param description is the description.
  */
  public void setDescription(String description);

  /** Get description.
  *@return the description
  */
  public String getDescription();

}
