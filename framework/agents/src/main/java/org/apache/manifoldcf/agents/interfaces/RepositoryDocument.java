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
import org.apache.manifoldcf.core.common.DateParser;
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

  // Security types
  public final static String SECURITY_TYPE_DOCUMENT = "document";
  public final static String SECURITY_TYPE_SHARE = "share";
  public final static String SECURITY_TYPE_PARENT = "parent";
  // Enumerated security type; add an integer to the end (deprecated)
  public final static String SECURITY_TYPE_DIRECTORY_LEVEL = "directory_";
  
  // Member variables.
  protected InputStream binaryFieldData = null;
  protected long binaryLength = 0;
  protected final Set<String> fieldSet = new HashSet<String>(); // MUST be independent of fields map because we iterate over this and may change fields
  protected final Map<String,Object> fields = new HashMap<String,Object>();
  protected final Map<String,String[]> stringFields = new HashMap<String,String[]>();
  protected final Map<String,Reader[]> readerFields = new HashMap<String,Reader[]>();
  protected final Map<String,Date[]> dateFields = new HashMap<String,Date[]>();
  protected final Map<String,Security> securityLevels = new HashMap<String,Security>();
  protected final List<String> sourcePath = new ArrayList<String>();
  protected final List<String> rootPath = new ArrayList<String>();
  protected String fileName = "docname";
  protected String contentMimeType = "application/octet-stream";
  protected Date createdDate = null;
  protected Date modifiedDate = null;
  protected Date indexingDate = null;
  protected Long originalSize = null;
  
  /** Constructor.
  */
  public RepositoryDocument()
  {
  }

  /** Create an exact duplicate of this Repository Document.  This is how you are expected to write
  * transformation connectors: you create a duplicate, and override the fields you want to change.
  * For streams etc, only the overridden fields need to be explicitly managed by the transformation
  * connector, since the original fields will be handled by the connector's caller.
  *@return the exact duplicate.
  */
  public RepositoryDocument duplicate()
  {
    RepositoryDocument rval = new RepositoryDocument();
    rval.binaryFieldData = binaryFieldData;
    rval.binaryLength = binaryLength;
    rval.fileName = fileName;
    rval.contentMimeType = contentMimeType;
    rval.createdDate = createdDate;
    rval.modifiedDate = modifiedDate;
    rval.indexingDate = indexingDate;
    rval.originalSize = originalSize;
    for (String key : fieldSet)
    {
      rval.fieldSet.add(key);
    }
    for (String key : fields.keySet())
    {
      rval.fields.put(key,fields.get(key));
    }
    for (String key : stringFields.keySet())
    {
      rval.stringFields.put(key,stringFields.get(key));
    }
    for (String key : readerFields.keySet())
    {
      rval.readerFields.put(key,readerFields.get(key));
    }
    for (String key : dateFields.keySet())
    {
      rval.dateFields.put(key,dateFields.get(key));
    }
    for (String key : securityLevels.keySet())
    {
      rval.securityLevels.put(key,securityLevels.get(key));
    }
    for (String pathElement : sourcePath)
    {
      rval.sourcePath.add(pathElement);
    }
    for (String pathElement : rootPath)
    {
      rval.rootPath.add(pathElement);
    }
    return rval;
  }
  
  /** Clear all fields.
  */
  public void clearFields()
  {
    fieldSet.clear();
    fields.clear();
    stringFields.clear();
    dateFields.clear();
    readerFields.clear();
  }
  
  /** Set the source path for the document.
  *@param sourcePath is the path.
  */
  public void setSourcePath(final List<String> sourcePath) {
    this.sourcePath.clear();
    for (final String pathElement : sourcePath) {
      this.sourcePath.add(pathElement);
    }
  }

  /** Get the source path for the document.
  *@return the source path.
  */
  public List<String> getSourcePath() {
    return sourcePath;
  }

  /** Set the root path for the document.
  * Must be a subset of the source path.
  *@param rootPath is the path.
  */
  public void setRootPath(final List<String> rootPath) {
    this.rootPath.clear();
    for (final String pathElement : rootPath) {
      this.rootPath.add(pathElement);
    }
  }

  /** Get the root path for the document.
  *@return the root path.
  */
  public List<String> getRootPath() {
    return rootPath;
  }
    
  /** Set the document's original (repository) size.  Use null to indicate that the size is
  * unknown.
  *@param size is the size.
  */
  public void setOriginalSize(Long size)
  {
    originalSize = size;
  }
  
  /** Get the document's original size.
  *@return the original repository document size, or null if unknown.
  */
  public Long getOriginalSize()
  {
    return originalSize;
  }
  
  /** Set the document's created date.  Use null to indicate that the date is unknown.
  *@param date is the date.
  */
  public void setCreatedDate(Date date)
  {
    createdDate = date;
  }
  
  /** Get the document's created date.  Returns null of the date is unknown.
  *@return the date.
  */
  public Date getCreatedDate()
  {
    return createdDate;
  }
  
  /** Set the document's last-modified date.  Use null to indicate that the date is unknown.
  *@param date is the date.
  */
  public void setModifiedDate(Date date)
  {
    modifiedDate = date;
  }
  
  /** Get the document's modified date.  Returns null of the date is unknown.
  *@return the date.
  */
  public Date getModifiedDate()
  {
    return modifiedDate;
  }

  /** Set the document's indexing date.  Use null to indicate that the date is unknown.
  *@param date is the date.
  */
  public void setIndexingDate(Date date)
  {
    indexingDate = date;
  }
  
  /** Get the document's indexing date.  Returns null of the date is unknown.
  *@return the date.
  */
  public Date getIndexingDate()
  {
    return indexingDate;
  }
  
  /** Set the document's mime type.
  *@param mimeType is the mime type.
  */
  public void setMimeType(String mimeType)
  {
    contentMimeType = mimeType;
  }
  
  /** Get the document's mime type.
  *@return the mime type.
  */
  public String getMimeType()
  {
    return contentMimeType;
  }
  
  /** Locate or create a specified security level.
  *@param securityType is the security type.
  */
  protected Security getSecurityLevel(String securityType)
  {
    Security s = securityLevels.get(securityType);
    if (s == null)
    {
      s = new Security();
      securityLevels.put(securityType, s);
    }
    return s;
  }
  
  /** Enumerate the active security types for this document.
  *@return an iterator over the security types.
  */
  public Iterator<String> securityTypesIterator()
  {
    return securityLevels.keySet().iterator();
  }
  
  /** Set security values for a given security type.
  *@param securityType is the security type.
  *@param acl is the acl.
  *@param denyAcl is the deny acl.
  */
  public void setSecurity(String securityType, String[] acl, String[] denyAcl)
  {
    if (acl != null && denyAcl != null)
    {
      Security s = getSecurityLevel(securityType);
      s.setACL(acl);
      s.setDenyACL(denyAcl);
    }
  }
  
  /** Set security acl for a given security type.
  *@param securityType is the security type.
  *@param acl is the acl;
  */
  public void setSecurityACL(String securityType, String[] acl)
  {
    if (acl != null)
    {
      Security s = getSecurityLevel(securityType);
      s.setACL(acl);
    }
  }
  
  /** Set security deny acl for a given security type.
  *@param securityType is the security type.
  *@param denyAcl is the deny acl.
  */
  public void setSecurityDenyACL(String securityType, String[] denyAcl)
  {
    if (denyAcl != null)
    {
      Security s = getSecurityLevel(securityType);
      s.setDenyACL(denyAcl);
    }
  }

  /** Get security acl for a given security type.
  *@param securityType is the security type.
  *@return the acl, which may be null.
  */
  public String[] getSecurityACL(String securityType)
  {
    Security s = securityLevels.get(securityType);
    if (s != null)
      return s.getACL();
    return null;
  }

  /** Get security deny acl for a given security type.
  *@param securityType is the security type.
  *@return the acl, which may be null.
  */
  public String[] getSecurityDenyACL(String securityType)
  {
    Security s = securityLevels.get(securityType);
    if (s != null)
      return s.getDenyACL();
    return null;
  }
  
  /** Set the binary field.
  * Data is described by a binary stream (which is expected to be processed),
  * This stream MUST BE CLOSED BY THE CALLER when the repository document instance has been ingested.
  * The stream also WILL NOT ever be reset; it is read to the end once only.
  *@param binaryFieldData is the input stream containing binary data.
  *@param binaryLength is the length of the stream, in bytes.  This is a REQUIRED parameter.
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
  
  /** Set the file name.
   *@param fileName is the file name.
   */
   public void setFileName(String fileName)
   {
     this.fileName = fileName;
   }

   /** Get the file Name.
   *@return the string of file name.
   */
   public String getFileName()
   {
     return fileName;
   }

  /** Get the binary length.
  *@return the length in bytes.
  */
  public long getBinaryLength()
  {
    return binaryLength;
  }

  /** Remove a field.
  *@param fieldName is the field name.
  */
  public void removeField(String fieldName)
  {
    fieldSet.remove(fieldName);
    fields.remove(fieldName);
    stringFields.remove(fieldName);
    readerFields.remove(fieldName);
    dateFields.remove(fieldName);
  }
  
  /** Add/remove a multivalue date field.
  *@param fieldName is the field name.
  *@param fieldData is the multi-valued data (an array of Dates).  Null means
  * to remove the entry.
  */
  public void addField(String fieldName, Date[] fieldData)
    throws ManifoldCFException
  {
    if (fieldData == null)
    {
      fieldSet.remove(fieldName);
      fields.remove(fieldName);
      stringFields.remove(fieldName);
      readerFields.remove(fieldName);
      dateFields.remove(fieldName);
    }
    else
    {
      fieldSet.add(fieldName);
      fields.put(fieldName,fieldData);
      stringFields.remove(fieldName);
      readerFields.remove(fieldName);
      dateFields.put(fieldName,fieldData);
    }
  }
  
  /** Add/remove a date field.
  *@param fieldName is the field name.
  *@param fieldData is the single-valued data (a Date).  Null means "no value".
  */
  public void addField(String fieldName, Date fieldData)
    throws ManifoldCFException
  {
    if (fieldData == null)
      addField(fieldName, (Date[])null);
    else
      addField(fieldName,new Date[]{fieldData});
  }

  /** Add/remove a multivalue character field.
  * Data is described here by an array of Readers (which are expected to be processed),
  * These Readers MUST BE CLOSED BY THE CALLER when the repository document instance has been ingested.
  * The Readers also WILL NOT ever be reset; they are read to the end once only.
  *@param fieldName is the field name.
  *@param fieldData is the multi-valued data (as an array of Readers).  Null means
  * to remove the entry from the document.
  */
  public void addField(String fieldName, Reader[] fieldData)
    throws ManifoldCFException
  {
    if (fieldData == null)
    {
      fieldSet.remove(fieldName);
      fields.remove(fieldName);
      stringFields.remove(fieldName);
      readerFields.remove(fieldName);
      dateFields.remove(fieldName);
    }
    else
    {
      fieldSet.add(fieldName);
      fields.put(fieldName,fieldData);
      stringFields.remove(fieldName);
      readerFields.put(fieldName,fieldData);
      dateFields.remove(fieldName);
    }
  }

  /** Add/remove a character field.
  * Data is described here by a Reader (which is expected to be processed),
  * This Reader MUST BE CLOSED BY THE CALLER when the repository document instance has been ingested.
  * The Reader also WILL NOT ever be reset; it is read to the end once only.
  *@param fieldName is the field name.
  *@param fieldData is the single-valued data (as a Reader).  Null means "no value".
  */
  public void addField(String fieldName, Reader fieldData)
    throws ManifoldCFException
  {
    if (fieldData == null)
      addField(fieldName, (Reader[])null);
    else
      addField(fieldName,new Reader[]{fieldData});
  }

  /** Add/Remove a multivalue character field.
  *@param fieldName is the field name.
  *@param fieldData is the multi-valued data (as a an array of Strings).  Null means
  * to remove the entry from the document.
  */
  public void addField(String fieldName, String[] fieldData)
    throws ManifoldCFException
  {
    if (fieldData == null)
    {
      fieldSet.remove(fieldName);
      fields.remove(fieldName);
      stringFields.remove(fieldName);
      readerFields.remove(fieldName);
      dateFields.remove(fieldName);
    }
    else
    {
      fieldSet.add(fieldName);
      fields.put(fieldName,fieldData);
      readerFields.remove(fieldName);
      stringFields.put(fieldName,fieldData);
      dateFields.remove(fieldName);
    }
  }

  /** Add a character field.
  *@param fieldName is the field name.
  *@param fieldData is the single-valued data (as a String).  Null means "no value".
  */
  public void addField(String fieldName, String fieldData)
    throws ManifoldCFException
  {
    if (fieldData == null)
      addField(fieldName,(String[])null);
    else
      addField(fieldName,new String[]{fieldData});
  }

  /** Get a field.
  *@param fieldName is the field name.
  *@return the field data (either a Reader array or a String array).
  */
  public Object[] getField(String fieldName)
  {
    return (Object[])fields.get(fieldName);
  }

  /** Get a field as an array of strings.  If the data was originally in the form
  * of Readers, a one-time conversion is made to the String form, so that the same
  * field can be fetched multiple times.  If the data was originally in the form
  * of Dates, then the dates are converted to standard ISO8601 format.
  *@param fieldName is the field name.
  *@return the field data.
  */
  public String[] getFieldAsStrings(String fieldName)
    throws IOException
  {
    String[] stringFieldData = stringFields.get(fieldName);
    if (stringFieldData != null)
      return stringFieldData;
    Date[] dateFieldData = dateFields.get(fieldName);
    if (dateFieldData != null)
    {
      String[] newValues = new String[dateFieldData.length];
      for (int i = 0; i < dateFieldData.length; i++)
      {
        newValues[i] = DateParser.formatISO8601Date(dateFieldData[i]);
      }
      return newValues;
    }
    Reader[] oldValues = readerFields.get(fieldName);
    if (oldValues != null)
    {
      String[] newValues = new String[oldValues.length];
      char[] buffer = new char[65536];
      for (int i = 0; i < newValues.length; i++)
      {
        Reader oldValue = oldValues[i];
        StringBuilder newValue = new StringBuilder();
        while (true)
        {
          int amt = oldValue.read(buffer);
          if (amt == -1)
            break;
          newValue.append(buffer,0,amt);
        }
        newValues[i] = newValue.toString();
      }
      stringFields.put(fieldName,newValues);
      // Reader is no longer useful, since we've read it to the end.
      // Remove it from the record accordingly.
      // NOTE WELL: This could cause side effects if the same
      // field is accessed simultaneously two different ways!
      readerFields.remove(fieldName);
      fields.put(fieldName,newValues);
      return newValues;
    }
    else
      return null;
  }

  /** Get a field as an array of Readers.  If the field was originally
  * strings, a one-time creation of a Readers array is made.
  *@param fieldName is the field name.
  *@return the field data.
  */
  public Reader[] getFieldAsReaders(String fieldName)
  {
    Reader[] readerFieldData = readerFields.get(fieldName);
    if (readerFieldData != null)
      return readerFieldData;
    Date[] dateFieldData = dateFields.get(fieldName);
    if (dateFieldData != null)
    {
      Reader[] newValues = new Reader[dateFieldData.length];
      for (int i = 0; i < newValues.length; i++)
      {
        newValues[i] = new StringReader(DateParser.formatISO8601Date(dateFieldData[i]));
      }
      readerFields.put(fieldName,newValues);
      return newValues;
    }
    String[] oldValues = stringFields.get(fieldName);
    if (oldValues != null)
    {
      Reader[] newValues = new Reader[oldValues.length];
      for (int i = 0; i < newValues.length; i++)
      {
        newValues[i] = new StringReader(oldValues[i]);
      }
      readerFields.put(fieldName,newValues);
      return newValues;
    }
    else
      return null;
  }

  /** Get field as an array of Date objects.
  * If the field was originally not a Date field, null is returned.
  *@param fieldName is the field name.
  *@return the field data.
  */
  public Date[] getFieldAsDates(String fieldName)
  {
    return dateFields.get(fieldName);
  }
  
  /** Get the number of fields.
  */
  public int fieldCount()
  {
    return fieldSet.size();
  }

  /** Iterate through the field name Strings.
  */
  public Iterator<String> getFields()
  {
    return fieldSet.iterator();
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

