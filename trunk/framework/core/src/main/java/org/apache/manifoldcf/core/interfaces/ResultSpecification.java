/* $Id: ResultSpecification.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;

/** An instance of this class is used to specify the result form of a column, if
* more than one form is possible.  If no form is specified, then a default form
* will be used.
*/
public class ResultSpecification
{
  public static final int FORM_DEFAULT = 0;
  public static final int FORM_STRING = 1;
  public static final int FORM_STREAM = 2;

  /** This map has a column name as a key, and a ColumnSpecification object as a value */
  protected HashMap columnSpecifications = new HashMap();

  /** Constructor */
  public ResultSpecification()
  {
  }

  /** Calculate a hash value **/
  public int hashCode()
  {
    String[] keys = new String[columnSpecifications.size()];
    Iterator iter = columnSpecifications.keySet().iterator();
    int i = 0;
    while (iter.hasNext())
    {
      keys[i++] = (String)iter.next();
    }
    java.util.Arrays.sort(keys);
    int rval = 0;
    i = 0;
    while (i < keys.length)
    {
      String key = keys[i++];
      rval += key.hashCode();
      ColumnSpecification cs = (ColumnSpecification)columnSpecifications.get(key);
      rval += cs.hashCode();
    }
    return rval;
  }

  /** Check equals */
  public boolean equals(Object o)
  {
    if (!(o instanceof ResultSpecification))
      return false;
    ResultSpecification other = (ResultSpecification)o;
    if (other.columnSpecifications.size() != columnSpecifications.size())
      return false;
    Iterator iter = columnSpecifications.keySet().iterator();
    while (iter.hasNext())
    {
      String key = (String)iter.next();
      ColumnSpecification thisSpec = (ColumnSpecification)columnSpecifications.get(key);
      ColumnSpecification otherSpec = (ColumnSpecification)other.columnSpecifications.get(key);
      if (otherSpec == null)
        return false;
      if (!thisSpec.equals(otherSpec))
        return false;
    }
    return true;
  }

  /** Convert to a unique string */
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    String[] keys = new String[columnSpecifications.size()];
    Iterator iter = columnSpecifications.keySet().iterator();
    int i = 0;
    while (iter.hasNext())
    {
      keys[i++] = (String)iter.next();
    }
    java.util.Arrays.sort(keys);
    sb.append(Integer.toString(keys.length));
    i = 0;
    while (i < keys.length)
    {
      String key = keys[i++];
      sb.append("-").append(key);
      ColumnSpecification cs = (ColumnSpecification)columnSpecifications.get(key);
      sb.append("-").append(cs.toString());
    }
    return sb.toString();
  }

  /** Specify that a column is to be read as a file stream, if possible */
  public void setForm(String columnName, int formValue)
  {
    ColumnSpecification cs = (ColumnSpecification)columnSpecifications.get(columnName);
    if (cs == null)
    {
      if (formValue == FORM_DEFAULT)
        return;
      cs = new ColumnSpecification();
      columnSpecifications.put(columnName,cs);
    }
    else
    {
      if (formValue == FORM_DEFAULT)
      {
        columnSpecifications.remove(columnName);
        return;
      }
    }
    cs.setForm(formValue);
  }

  /** Get the specified form of a column */
  public int getForm(String columnName)
  {
    ColumnSpecification cs = (ColumnSpecification)columnSpecifications.get(columnName);
    if (cs == null)
      return FORM_DEFAULT;
    return cs.getForm();
  }

  protected static class ColumnSpecification
  {
    protected int formValue;

    public ColumnSpecification()
    {
      formValue = FORM_DEFAULT;
    }

    public void setForm(int formValue)
    {
      this.formValue = formValue;
    }

    public int getForm()
    {
      return formValue;
    }

    public String toString()
    {
      return Integer.toString(formValue);
    }

    public int hashCode()
    {
      return formValue;
    }

    public boolean equals(Object o)
    {
      if (!(o instanceof ColumnSpecification))
        return false;
      ColumnSpecification other = (ColumnSpecification)o;
      return other.formValue == formValue;
    }
  }

}
