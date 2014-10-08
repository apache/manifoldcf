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

import java.io.*;
import java.util.*;

/** This interface abstracts from the activities that a transformation connector can do
when checking a document.
*/
public interface IOutputCheckActivity
{
  public static final String _rcsid = "@(#)$Id$";

  /** Detect if a document date is acceptable downstream or not.  This method is used to determine whether it makes sense to fetch a document
  * in the first place.
  *@param date is the date of the document.
  *@return true if the document with that date can be accepted by the downstream connection.
  */
  public boolean checkDateIndexable(Date date)
    throws ManifoldCFException, ServiceInterruption;

  /** Detect if a mime type is acceptable downstream or not.  This method is used to determine whether it makes sense to fetch a document
  * in the first place.
  *@param mimeType is the mime type of the document.
  *@return true if the mime type can be accepted by the downstream connection.
  */
  public boolean checkMimeTypeIndexable(String mimeType)
    throws ManifoldCFException, ServiceInterruption;

  /** Pre-determine whether a document (passed here as a File object) is acceptable downstream.  This method is
  * used to determine whether a document needs to be actually transferred.  This hook is provided mainly to support
  * search engines that only handle a small set of accepted file types.
  *@param localFile is the local file to check.
  *@return true if the file is acceptable by the downstream connection.
  */
  public boolean checkDocumentIndexable(File localFile)
    throws ManifoldCFException, ServiceInterruption;

  /** Pre-determine whether a document's length is acceptable downstream.  This method is used
  * to determine whether to fetch a document in the first place.
  *@param length is the length of the document.
  *@return true if the file is acceptable by the downstream connection.
  */
  public boolean checkLengthIndexable(long length)
    throws ManifoldCFException, ServiceInterruption;

  /** Pre-determine whether a document's URL is acceptable downstream.  This method is used
  * to help filter out documents that cannot be indexed in advance.
  *@param url is the URL of the document.
  *@return true if the file is acceptable by the downstream connection.
  */
  public boolean checkURLIndexable(String url)
    throws ManifoldCFException, ServiceInterruption;

}
