/* $Id: ColumnDescription.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** Describe a database column.
*/
public class ColumnDescription
{
  public static final String _rcsid = "@(#)$Id: ColumnDescription.java 988245 2010-08-23 18:39:35Z kwright $";

  protected String typeString;
  protected boolean isPrimaryKey;
  protected boolean isNull;
  protected String referenceTable;
  protected String referenceColumn;
  protected boolean referenceCascade;

  /** Create a column type description.
  * Use the output of this method in the columnMap with performCreate().
  *@param typeString is a type specification
  *@param isPrimaryKey describes whether the column is a primary key
  *@param isNull describes whether the column is nullable
  *@param referenceTable describes the reference table, if any.
  *@param referenceCascade is true if deletes should be cascaded.
  *returns a column description object.
  */
  public ColumnDescription(String typeString, boolean isPrimaryKey,
    boolean isNull, String referenceTable, String referenceColumn,
    boolean referenceCascade)
  {
    this.typeString = typeString;
    this.isPrimaryKey = isPrimaryKey;
    this.isNull = isNull;
    this.referenceTable = referenceTable;
    this.referenceColumn = referenceColumn;
    this.referenceCascade = referenceCascade;
  }

  public String getTypeString()
  {
    return typeString;
  }

  public boolean getIsPrimaryKey()
  {
    return isPrimaryKey;
  }

  public boolean getIsNull()
  {
    return isNull;
  }

  public String getReferenceTable()
  {
    return referenceTable;
  }

  public String getReferenceColumn()
  {
    return referenceColumn;
  }

  public boolean getReferenceCascade()
  {
    return referenceCascade;
  }

}

