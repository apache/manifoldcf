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
public class VariableArray extends VariableBase
{
  protected List<Variable> array = new ArrayList<Variable>();
  
  public VariableArray()
  {
  }

  /** Get a displayable string from this */
  public String toString()
  {
    StringBuilder sb = new StringBuilder("[ ");
    int i = 0;
    while (i < array.size())
    {
      if (i > 0)
        sb.append(", ");
      Variable v = array.get(i++);
      if (v == null)
        sb.append("null");
      else
        sb.append(v.toString());
    }
    sb.append(" ]");
    return sb.toString();
  }
  
  /** Get a named attribute of the variable; e.g. xxx.yyy */
  public VariableReference getAttribute(String attributeName)
    throws ScriptException
  {
    // We recognize only the __size__ attribute
    if (attributeName.equals(ATTRIBUTE_SIZE))
      return new VariableInt(array.size());
    return super.getAttribute(attributeName);
  }
  
  /** Get an indexed property of the variable */
  public VariableReference getIndexed(int index)
    throws ScriptException
  {
    if (index < array.size())
      return new ElementReference(index);
    return super.getIndexed(index);
  }
  
  /** Insert an object into this variable at a position. */
  public void insertAt(Variable v, int index)
    throws ScriptException
  {
    if (index > array.size())
      throw new ScriptException("Insert out of bounds");
    array.add(index,v);
  }

  /** Insert an object into this variable at end. */
  public void insert(Variable v)
    throws ScriptException
  {
    array.add(v);
  }

  /** Delete an object from this variable at a position. */
  public void removeAt(int index)
    throws ScriptException
  {
    if (index >= array.size())
      throw new ScriptException("Remove out of bounds");
    array.remove(index);
  }

  /** Extend VariableReference class so we capture attempts to set the reference, and actually overwrite the child when that is done */
  protected class ElementReference implements VariableReference
  {
    protected int index;
    
    public ElementReference(int index)
    {
      this.index = index;
    }
    
    public void setReference(Variable v)
      throws ScriptException
    {
      if (index >= array.size())
        throw new ScriptException("Index out of range for array children");
      array.set(index,v);
    }
    
    public Variable resolve()
      throws ScriptException
    {
      if (index >= array.size())
        throw new ScriptException("Index out of range for array children");
      return array.get(index);
    }
    
    /** Check if this reference is null */
    public boolean isNull()
    {
      return index >= array.size() || array.get(index) == null;
    }

  }
}
