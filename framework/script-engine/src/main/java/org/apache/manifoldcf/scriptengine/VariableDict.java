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

package org.apache.manifoldcf.scriptengine;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;

/** Array variable object.
*/
public class VariableDict extends VariableBase
{
  protected Map<Variable,VariableReference> dict = new HashMap<Variable,VariableReference>();
  
  public VariableDict()
  {
  }
  
  /** Get a named attribute of the variable; e.g. xxx.yyy */
  @Override
  public VariableReference getAttribute(String attributeName)
    throws ScriptException
  {
    // We recognize only the __size__ attribute
    if (attributeName.equals(ATTRIBUTE_SIZE))
      return new VariableInt(dict.size());
    return super.getAttribute(attributeName);
  }
  
  /** Get an indexed property of the variable */
  @Override
  public VariableReference getIndexed(Variable index)
    throws ScriptException
  {
    if (index == null)
      throw new ScriptException(composeMessage("Subscript cannot be null"));
    VariableReference rval = dict.get(index);
    if (rval == null)
    {
      rval = new ContextVariableReference();
      dict.put(index,rval);
    }
    return rval;
  }
  
}
