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
package org.apache.lcf.core.interfaces;

import java.io.*;

/** This class represents a lightweight length-determined character stream.  It is used
* as a parameter in parameterized queries that use strings.
* There are no implied semantics in this class around managing the stream itself.
* These semantics must be handled by a derived class.
*/
public abstract class CharacterInput
{
	public static final String _rcsid = "@(#)$Id$";

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
		throws MetacartaException
	{
		if (stream == null)
			openStream();
		return stream;
	}

	public void doneWithStream()
		throws MetacartaException
	{
		if (stream != null)
			closeStream();
	}

	public long getCharacterLength()
		throws MetacartaException
	{
		if (charLength == -1L)
			calculateLength();
		return charLength;
	}
	
	public String getHashValue()
		throws MetacartaException
	{
		if (hashValue == null)
			calculateHashValue();
		return hashValue;
	}
	
	/** Open a Utf8 stream directly */
	public abstract InputStream getUtf8Stream()
		throws MetacartaException;

	/** Transfer to a new object; this causes the current object to become "already discarded" */
	public abstract CharacterInput transfer();
	
	/** Discard this object permanently */
	public void discard()
		throws MetacartaException
	{
		doneWithStream();
	}
	
	// Protected methods
	
	/** Open a reader, for use by a caller, until closeStream is called */
	protected abstract void openStream()
		throws MetacartaException;
	
	/** Close any open reader */
	protected void closeStream()
		throws MetacartaException
	{
		try
		{
			stream.close();
			stream = null;
		}
		catch (InterruptedIOException e)
		{
			throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
		}
		catch (IOException e)
		{
			throw new MetacartaException("Error closing stream: "+e.getMessage(),e,MetacartaException.GENERAL_ERROR);
		}
	}
	
	/** Calculate the datum's length in characters */
	protected abstract void calculateLength()
		throws MetacartaException;
	
	/** Calculate the datum's hash value */
	protected abstract void calculateHashValue()
		throws MetacartaException;
	
}
