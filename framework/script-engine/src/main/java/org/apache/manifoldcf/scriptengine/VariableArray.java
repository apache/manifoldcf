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
  
  @Override
  public int hashCode()
  {
    int rval = 0;
    int i = 0;
    while (i < array.size())
    {
      Variable v = array.get(i++);
      rval += v.hashCode();
    }
    return rval;
  }

  @Override
  public boolean equals(Object o)
  {
    if (!(o instanceof VariableArray))
      return false;
    VariableArray va = (VariableArray)o;
    if (va.array.size() != array.size())
      return false;
    int i = 0;
    while (i < array.size())
    {
      Variable v = array.get(i);
      Variable v2 = va.array.get(i);
      if (!v.equals(v2))
        return false;
      i++;
    }
    return true;
  }
  
  /** Check if the variable has a script value */
  @Override
  public boolean hasScriptValue()
    throws ScriptException
  {
    return true;
  }

  /** Get the variable's script value */
  @Override
  public String getScriptValue()
    throws ScriptException
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
        sb.append(v.getScriptValue());
    }
    sb.append(" ]");
    return sb.toString();
  }
  
  /** Get a named attribute of the variable; e.g. xxx.yyy */
  @Override
  public VariableReference getAttribute(String attributeName)
    throws ScriptException
  {
    // We recognize only the __size__ attribute
    if (attributeName.equals(ATTRIBUTE_SIZE))
      return new VariableInt(array.size());
    return super.getAttribute(attributeName);
  }
  
  /** Get an indexed property of the variable */
  @Override
  public VariableReference getIndexed(Variable index)
    throws ScriptException
  {
    if (index == null)
      throw new ScriptException(composeMessage("Subscript cannot be null"));
    int indexValue = index.getIntValue();
    if (indexValue >= 0 && indexValue < array.size())
      return new ElementReference(indexValue);
    throw new ScriptException(composeMessage("Index out of bounds: "+indexValue));
  }
  
  /** Insert an object into this variable at a position. */
  @Override
  public void insertAt(Variable v, Variable index)
    throws ScriptException
  {
    if (index == null)
      array.add(v);
    else
    {
      int indexValue = index.getIntValue();
      if (indexValue < 0 || indexValue > array.size())
        throw new ScriptException(composeMessage("Insert index out of bounds: "+indexValue));
      array.add(indexValue,v);
    }
  }

  /** Delete an object from this variable at a position. */
  @Override
  public void removeAt(Variable index)
    throws ScriptException
  {
    if (index == null)
      throw new ScriptException(composeMessage("Array remove index cannot be null"));
    int indexValue = index.getIntValue();
    if (indexValue < 0 || indexValue >= array.size())
      throw new ScriptException(composeMessage("Array remove index out of bounds: "+indexValue));
    array.remove(indexValue);
  }

  /** Extend VariableReference class so we capture attempts to set the reference, and actually overwrite the child when that is done */
  protected class ElementReference implements VariableReference
  {
    protected int index;
    
    public ElementReference(int index)
    {
      this.index = index;
    }

    @Override
    public void setReference(Variable v)
      throws ScriptException
    {
      if (index < 0 || index >= array.size())
        throw new ScriptException(composeMessage("Index out of range for array children: "+index));
      array.set(index,v);
    }
    
    @Override
    public Variable resolve()
      throws ScriptException
    {
      if (index < 0 || index >= array.size())
        throw new ScriptException(composeMessage("Index out of range for array children: "+index));
      return array.get(index);
    }
    
    /** Check if this reference is null */
    @Override
    public boolean isNull()
    {
      return index < 0 || index >= array.size() || array.get(index) == null;
    }

  }
}
