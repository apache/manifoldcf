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
import org.apache.manifoldcf.agents.interfaces.*;

/** This interface abstracts from the activities that an output connector can do
when qualifying authority names.
*/
public interface IOutputQualifyActivity
{
  public static final String _rcsid = "@(#)$Id$";

  /** Qualify an access token appropriately, to match access tokens as returned by mod_aa.  This method
  * includes the authority name with the access token, if any, so that each authority may establish its own token space.
  *@param authorityNameString is the name of the authority to use to qualify the access token.
  *@param accessToken is the raw, repository access token.
  *@return the properly qualified access token.
  */
  public String qualifyAccessToken(String authorityNameString, String accessToken)
    throws ManifoldCFException;
}
