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

/** This class represents a lightweight length-determined stream.  It is used
* as a parameter in parameterized queries that use blobs.
* There are no implied semantics in this class around managing the stream itself.
* These semantics must be handled by a derived class.
*/
public abstract class BinaryInput
{
        public static final String _rcsid = "@(#)$Id$";

        protected InputStream stream;
        protected long length;

        /** Construct from nothing.
        */
        public BinaryInput()
        {
                stream = null;
                length = -1L;
        }

        /** Obtain the stream to pass to JDBC */
        public InputStream getStream()
                throws LCFException
        {
                if (stream == null)
                        openStream();
                return stream;
        }

        /** Obtain the length to pass to JDBC */
        public long getLength()
                throws LCFException
        {
                if (length == -1L)
                        calculateLength();
                return length;
        }

        /** Close the stream we passed to JDBC */
        public void doneWithStream()
                throws LCFException
        {
                if (stream != null)
                        closeStream();
        }
        
        /** Transfer to a new object; this causes the current object to become "already discarded" */
        public abstract BinaryInput transfer();

        /** Discard the object */
        public void discard()
                throws LCFException
        {
                doneWithStream();
        }
        
        // Protected methods
        
        protected abstract void openStream()
                throws LCFException;
        
        protected abstract void calculateLength()
                throws LCFException;

        /** Close the stream */
        protected void closeStream()
                throws LCFException
        {
                try
                {
                        stream.close();
                        stream = null;
                }
                catch (InterruptedIOException e)
                {
                        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
                }
                catch (IOException e)
                {
                        throw new LCFException("IO exception closing stream: "+e.getMessage(),e,LCFException.GENERAL_ERROR);
                }
        }
        
}
