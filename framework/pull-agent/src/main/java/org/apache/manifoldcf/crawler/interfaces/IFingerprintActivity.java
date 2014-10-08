/* $Id: IFingerprintActivity.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.agents.interfaces.*;
import java.io.*;
import java.util.*;

/** This interface abstracts from the activities that handle document fingerprinting and mime type acceptance.
*/
public interface IFingerprintActivity
{
  public static final String _rcsid = "@(#)$Id: IFingerprintActivity.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Detect if a date is indexable or not.  This method is used by participating repository connectors to pre-filter the number of
  * unusable documents that will be passed to this output connector.
  *@param date is the date of the document; may be null
  *@return true if a document with that date is indexable by this connector.
  */
  public boolean checkDateIndexable(Date date)
    throws ManifoldCFException, ServiceInterruption;

  /** Detect if a mime type is indexable or not.  This method is used by participating repository connectors to pre-filter the number of
  * unusable documents that will be passed to this output connector.
  *@param mimeType is the mime type of the document.
  *@return true if the mime type is indexable by this connector.
  */
  public boolean checkMimeTypeIndexable(String mimeType)
    throws ManifoldCFException, ServiceInterruption;

  /** Check whether a document is indexable by the currently specified output connector.
  *@param localFile is the local copy of the file to check.
  *@return true if the document is indexable.
  */
  public boolean checkDocumentIndexable(File localFile)
    throws ManifoldCFException, ServiceInterruption;

  /** Check whether a document of a specific length is indexable by the currently specified output connector.
  *@param length is the document length.
  *@return true if the document is indexable.
  */
  public boolean checkLengthIndexable(long length)
    throws ManifoldCFException, ServiceInterruption;

  /** Pre-determine whether a document's URL is indexable by this connector.  This method is used by participating repository connectors
  * to help filter out documents that are not worth indexing.
  *@param url is the URL of the document.
  *@return true if the file is indexable.
  */
  public boolean checkURLIndexable(String url)
    throws ManifoldCFException, ServiceInterruption;

}
