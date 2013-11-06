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
package org.apache.manifoldcf.core.interfaces;

import java.io.*;

import org.apache.manifoldcf.core.system.ManifoldCF;

/** This class represents a null character stream, which has no characters.
*/
public class NullCharacterInput extends CharacterInput
{
  public static final String _rcsid = "@(#)$Id$";

  /** Construct from nothing.
  */
  public NullCharacterInput()
  {
    super();
  }

  @Override
  public Reader getStream()
    throws ManifoldCFException
  {
    return new StringReader("");
  }

  @Override
  public void doneWithStream()
    throws ManifoldCFException
  {
  }

  @Override
  public long getCharacterLength()
    throws ManifoldCFException
  {
    return 0L;
  }

  @Override
  public String getHashValue()
    throws ManifoldCFException
  {
    return ManifoldCF.getHashValue(ManifoldCF.startHash());
  }

  /** Open a Utf8 stream directly */
  @Override
  public InputStream getUtf8Stream()
    throws ManifoldCFException
  {
    return new ByteArrayInputStream(new byte[]{});
  }

  /** Get binary UTF8 stream length directly */
  @Override
  public long getUtf8StreamLength()
    throws ManifoldCFException
  {
    return 0L;
  }

  /** Transfer to a new object; this causes the current object to become "already discarded" */
  @Override
  public CharacterInput transfer()
  {
    return new NullCharacterInput();
  }

  /** Discard this object permanently */
  @Override
  public void discard()
    throws ManifoldCFException
  {
  }

  // Protected methods

  /** Open a reader, for use by a caller, until closeStream is called */
  @Override
  protected void openStream()
    throws ManifoldCFException
  {
  }

  /** Close any open reader */
  @Override
  protected void closeStream()
    throws ManifoldCFException
  {
  }

  /** Calculate the datum's length in characters */
  @Override
  protected void calculateLength()
    throws ManifoldCFException
  {
  }

  /** Calculate the datum's hash value */
  @Override
  protected void calculateHashValue()
    throws ManifoldCFException
  {
  }

}
