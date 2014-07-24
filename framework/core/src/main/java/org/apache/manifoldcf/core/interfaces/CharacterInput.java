/* $Id: CharacterInput.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import java.io.*;

/** This class represents a lightweight length-determined character stream.  It is used
* as a parameter in parameterized queries that use strings.
* There are no implied semantics in this class around managing the stream itself.
* These semantics must be handled by a derived class.
*/
public abstract class CharacterInput extends PersistentDatabaseObject
{
  public static final String _rcsid = "@(#)$Id: CharacterInput.java 988245 2010-08-23 18:39:35Z kwright $";

  protected Reader stream = null;
  protected long charLength = -1L;
  protected String hashValue = null;

  /** Construct from nothing.
  */
  public CharacterInput()
  {
    stream = null;
    charLength = -1L;
  }

  public Reader getStream()
    throws ManifoldCFException
  {
    if (stream == null)
      openStream();
    return stream;
  }

  @Override
  public void doneWithStream()
    throws ManifoldCFException
  {
    if (stream != null)
      closeStream();
  }

  public long getCharacterLength()
    throws ManifoldCFException
  {
    if (charLength == -1L)
      calculateLength();
    return charLength;
  }

  public String getHashValue()
    throws ManifoldCFException
  {
    if (hashValue == null)
      calculateHashValue();
    return hashValue;
  }

  /** Open a Utf8 stream directly */
  public abstract InputStream getUtf8Stream()
    throws ManifoldCFException;

  /** Get binary UTF8 stream length directly */
  public abstract long getUtf8StreamLength()
    throws ManifoldCFException;

  /** Transfer to a new object; this causes the current object to become "already discarded" */
  public abstract CharacterInput transfer();

  /** Discard this object permanently */
  @Override
  public void discard()
    throws ManifoldCFException
  {
    doneWithStream();
  }

  // Protected methods

  /** Open a reader, for use by a caller, until closeStream is called */
  protected abstract void openStream()
    throws ManifoldCFException;

  /** Close any open reader */
  protected void closeStream()
    throws ManifoldCFException
  {
    try
    {
      stream.close();
      stream = null;
    }
    catch (IOException e)
    {
      handleIOException(e, "closing stream");
    }
  }

  /** Calculate the datum's length in characters */
  protected abstract void calculateLength()
    throws ManifoldCFException;

  /** Calculate the datum's hash value */
  protected abstract void calculateHashValue()
    throws ManifoldCFException;

  protected static void handleIOException(IOException e, String context)
    throws ManifoldCFException
  {
    if (e instanceof InterruptedIOException)
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    throw new ManifoldCFException("IO exception while "+context+": "+e.getMessage(),e);
  }

}
