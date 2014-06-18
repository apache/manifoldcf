/* $Id: SortOrder.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import java.util.*;

/** Class which describes specification of the sort order for a report.
*/
public class SortOrder
{
  public static final String _rcsid = "@(#)$Id: SortOrder.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Sort ascending */
  public static final int SORT_ASCENDING = 0;
  /** Sort descending */
  public static final int SORT_DESCENDING = 1;

  /** The sort order list.  This is an array of SortSpec objects */
  protected ArrayList sortList = new ArrayList();

  /** Constructor.
  */
  public SortOrder()
  {
  }

  /** Constructor from string representation.
  */
  public SortOrder(String rep)
    throws ManifoldCFException
  {
    ParseBuffer pb = new ParseBuffer(rep);
    StringBuilder numBuffer = new StringBuilder();
    while (true)
    {
      int x = pb.peekCharAt();
      if (x == -1)
        throw new ManifoldCFException("Unexpected end");
      char y = (char)x;
      pb.next();
      if (y == ':')
        break;
      numBuffer.append(y);
    }
    try
    {
      int numCount = Integer.parseInt(numBuffer.toString());
      int i = 0;
      while (i < numCount)
      {
        SortSpec ss = new SortSpec(pb);
        sortList.add(ss);
        i++;
      }
    }
    catch (NumberFormatException e)
    {
      throw new ManifoldCFException("Bad number",e);
    }
  }

  /** Convert to string form.
  */
  public String toString()
  {
    StringBuilder output = new StringBuilder();
    output.append(Integer.toString(sortList.size()));
    output.append(":");
    int i = 0;
    while (i < sortList.size())
    {
      SortSpec ss = (SortSpec)sortList.get(i++);
      output.append(ss.toString());
    }
    return output.toString();
  }

  /** Click a column.
  */
  public void clickColumn(String columnName)
  {
    int findIndex = -1;
    int i = 0;
    while (i < sortList.size())
    {
      SortSpec ss = (SortSpec)sortList.get(i);
      if (ss.getColumn().equals(columnName))
        findIndex = i;
      i++;
    }
    if (findIndex == -1)
      addCriteria(columnName,SORT_ASCENDING);
    else
    {
      if (findIndex == 0)
      {
        // Flip the sort order of the first entry
        SortSpec ss = (SortSpec)sortList.get(findIndex);
        sortList.remove(findIndex);
        addCriteria(columnName,(ss.getDirection()==SORT_ASCENDING)?SORT_DESCENDING:SORT_ASCENDING);
      }
      else
      {
        // Just move it around to the front
        SortSpec ss = (SortSpec)sortList.remove(findIndex);
        sortList.add(0,ss);
      }
    }
  }

  /** Add a sort criteria, at the front.
  */
  public void addCriteria(String columnName, int order)
  {
    sortList.add(0,new SortSpec(columnName,order));
  }

  /** Get the sort spec count.
  */
  public int getCount()
  {
    return sortList.size();
  }

  /** Return an individual sort column.
  */
  public String getColumn(int i)
  {
    return ((SortSpec)sortList.get(i)).getColumn();
  }

  /** Return an individual direction.
  */
  public int getDirection(int i)
  {
    return ((SortSpec)sortList.get(i)).getDirection();
  }

  public static class ParseBuffer
  {
    protected String value;
    protected int startPosition;

    public ParseBuffer(String value)
    {
      this.value = value;
      startPosition = 0;
    }

    public int peekCharAt()
    {
      if (startPosition == value.length())
        return -1;
      return (int)value.charAt(startPosition);
    }

    public void next()
    {
      if (startPosition < value.length())
        startPosition++;
    }
  }

  public static class SortSpec
  {
    protected String column;
    protected int direction;

    public SortSpec(String column, int direction)
    {
      this.column = column;
      this.direction = direction;
    }

    public SortSpec(ParseBuffer pb)
      throws ManifoldCFException
    {
      int x = pb.peekCharAt();
      if (x == -1)
        throw new ManifoldCFException("Unexpected end");
      char y = (char)x;
      if (y == '+')
        this.direction = SORT_ASCENDING;
      else if (y == '-')
        this.direction = SORT_DESCENDING;
      else
        throw new ManifoldCFException("Bad direction");
      pb.next();
      StringBuilder sb = new StringBuilder();
      while (true)
      {
        x = pb.peekCharAt();
        if (x == -1)
          throw new ManifoldCFException("Unexpected end");
        y = (char)x;
        pb.next();
        if (y == '.')
          break;
        sb.append(y);
      }
      column = sb.toString();
    }

    public String getColumn()
    {
      return column;
    }

    public int getDirection()
    {
      return direction;
    }

    public String toString()
    {
      StringBuilder output = new StringBuilder();
      if (direction == SORT_ASCENDING)
        output.append("+");
      else
        output.append("-");
      output.append(column).append(".");
      return output.toString();
    }
  }

}
