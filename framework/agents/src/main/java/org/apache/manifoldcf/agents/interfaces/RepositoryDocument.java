/* $Id: RepositoryDocument.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import java.util.*;
import java.io.*;

/** This class contains the complete information for a document, as read
* from a repository.  The generator of this document is one of the
* repository connectors; the user of the class is the incremental ingester.
*
* Data contained within is described in part by a binary stream (which is expected to be processed),
* and partly by already-extracted textual data.  These
* streams MUST BE CLOSED BY THE CALLER when the repository document instance has been ingested.
* The streams also WILL NOT ever be reset; they are read to the end once only.
*/
public class RepositoryDocument
{
  public static final String _rcsid = "@(#)$Id: RepositoryDocument.java 988245 2010-08-23 18:39:35Z kwright $";

  // Member variables.
  protected InputStream binaryFieldData = null;
  protected long binaryLength = 0;
  protected Map<String,Object> fields = new HashMap<String,Object>();
  protected Security fileSecurity = new Security();
  protected Security shareSecurity = new Security();
  protected List<Security> directorySecurity = new ArrayList<Security>();

  /** Constructor.
  */
  public RepositoryDocument()
  {
  }

  /** Set the document's "file" allow acls.
  *@param acl is the allowed "file" access control token list for the document.
  */
  public void setACL(String[] acl)
  {
    fileSecurity.setACL(acl);
  }

  /** Get the document's "file" allow acl, if any.
  *@return the allow access control token list for the document.
  */
  public String[] getACL()
  {
    return fileSecurity.getACL();
  }

  /** Set the document's "file" deny acl.
  *@param acl is the "file" denied access control token list for the document.
  */
  public void setDenyACL(String[] acl)
  {
    fileSecurity.setDenyACL(acl);
  }

  /** Get the document's deny acl, if any.
  *@return the deny access control token list for the document.
  */
  public String[] getDenyACL()
  {
    return fileSecurity.getDenyACL();
  }

  /** Set document's "share" acl. */
  public void setShareACL(String[] acl)
  {
    shareSecurity.setACL(acl);
  }

  /** Get document's "share" acl. */
  public String[] getShareACL()
  {
    return shareSecurity.getACL();
  }

  /** Set document's "share" deny acl. */
  public void setShareDenyACL(String[] acl)
  {
    shareSecurity.setDenyACL(acl);
  }

  /** Get document's "share" deny acl. */
  public String[] getShareDenyACL()
  {
    return shareSecurity.getDenyACL();
  }

  /** Clear all directory acls. */
  public void clearDirectoryACLs()
  {
    directorySecurity.clear();
  }

  /** Get a count of directory security entries. */
  public int countDirectoryACLs()
  {
    return directorySecurity.size();
  }

  /** Add directory security entry */
  public void addDirectoryACLs(String[] allowACL, String[] denyACL)
  {
    Security s = new Security();
    s.setACL(allowACL);
    s.setDenyACL(denyACL);
    directorySecurity.add(s);
  }

  /** Get directory security access acl */
  public String[] getDirectoryACL(int index)
  {
    Security s = directorySecurity.get(index);
    return s.getACL();
  }

  /** Get directory security deny acl */
  public String[] getDirectoryDenyACL(int index)
  {
    Security s = directorySecurity.get(index);
    return s.getDenyACL();
  }

  /** Set the binary field.
  *@param binaryFieldData is the input stream containing binary data.
  */
  public void setBinary(InputStream binaryFieldData, long binaryLength)
  {
    this.binaryFieldData = binaryFieldData;
    this.binaryLength = binaryLength;
  }

  /** Get the binary fields (if any).
  *@return the binary stream.
  */
  public InputStream getBinaryStream()
  {
    return binaryFieldData;
  }

  /** Get the binary length.
  *@return the length in bytes.
  */
  public long getBinaryLength()
  {
    return binaryLength;
  }

  /** Add a multivalue character field.
  *@param fieldName is the field name.
  *@param fieldData is the multi-valued data (as an array of Readers).  Null means
  * to remove the entry from the document.
  */
  public void addField(String fieldName, Reader[] fieldData)
    throws ManifoldCFException
  {
    if (fieldData == null)
      fields.remove(fieldName);
    else
      fields.put(fieldName,fieldData);
  }

  /** Add a character field.
  *@param fieldName is the field name.
  *@param fieldData is the single-valued data (as a Reader).  Null means "no value".
  */
  public void addField(String fieldName, Reader fieldData)
    throws ManifoldCFException
  {
    fields.put(fieldName,new Reader[]{fieldData});
  }

  /** Remove a multivalue character field.
  *@param fieldName is the field name.
  *@param fieldData is the multi-valued data (as a an array of Strings).  Null means
  * to remove the entry from the document.
  */
  public void addField(String fieldName, String[] fieldData)
    throws ManifoldCFException
  {
    if (fieldData == null)
      fields.remove(fieldName);
    else
      fields.put(fieldName,fieldData);
  }

  /** Add a character field.
  *@param fieldName is the field name.
  *@param fieldData is the single-valued data (as a String).  Null means "no value".
  */
  public void addField(String fieldName, String fieldData)
    throws ManifoldCFException
  {
    fields.put(fieldName,new String[]{fieldData});
  }

  /** Get a field.
  *@param fieldName is the field name.
  *@return the field data (either a Reader array or a String array).
  */
  public Object[] getField(String fieldName)
  {
    return (Object[])fields.get(fieldName);
  }

  /** Get the number of fields.
  */
  public int fieldCount()
  {
    return fields.size();
  }

  /** Iterate through the field name Strings.
  */
  public Iterator<String> getFields()
  {
    return fields.keySet().iterator();
  }

  /** This class describes allow and deny tokens for a specific security class. */
  protected static class Security
  {
    /** Allow tokens */
    protected String[] tokens = null;
    /** Deny tokens */
    protected String[] denyTokens = null;

    /** Constructor. */
    public Security()
    {
    }

    /** Set allow tokens. */
    public void setACL(String[] tokens)
    {
      this.tokens = tokens;
    }

    /** Get allow tokens */
    public String[] getACL()
    {
      return tokens;
    }

    /** Set deny tokens */
    public void setDenyACL(String[] tokens)
    {
      denyTokens = tokens;
    }

    /** Get deny tokens */
    public String[] getDenyACL()
    {
      return denyTokens;
    }
  }

}

