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
import org.apache.lcf.core.system.LCF;

/** This class represents a temporary file character input
* stream.  Call the "done" method to clean up the
* file when done.
* NOTE: The implied flow of this method is to be handled
* a file that has already been created by some means.  The
* file must be a dedicated temporary file, which can be
* destroyed when the data has been used.
*/
public class TempFileCharacterInput extends CharacterInput
{
        public static final String _rcsid = "@(#)$Id$";

        protected File file;

        protected final static int CHUNK_SIZE = 65536;

        /** Construct from a length-delimited reader.
        *@param is is a reader to transfer from, to the end of the data.  This will, as a side effect, also calculate the character length
        *          and hash value for the data.
        */
        public TempFileCharacterInput(Reader is)
                throws LCFException
        {
                super();
                try
                {
                        // Create a temporary file to put the stuff in
                        File outfile = File.createTempFile("_MC_","");
                        try
                        {
                                // Register the file for autodeletion, using our infrastructure.
                                LCF.addFile(outfile);
                                // deleteOnExit() causes memory leakage!
                                // outfile.deleteOnExit();
                                
                                // Set up hash digest and character length counter before we start anything.
                                java.security.MessageDigest md = LCF.startHash();
                                
                                FileOutputStream outStream = new FileOutputStream(outfile);
                                // Create a Writer corresponding to the file output stream, and encode using utf-8
                                OutputStreamWriter outWriter = new OutputStreamWriter(outStream,"utf-8");
                                try
                                {
                                        char[] buffer = new char[CHUNK_SIZE];
                                        long totalMoved = 0;
                                        while (true)
                                        {
                                                int moveAmount = CHUNK_SIZE;
                                                // Read character data in 64K chunks
                                                int readsize = is.read(buffer,0,moveAmount);
                                                if (readsize == -1)
                                                        break;
                                                outWriter.write(buffer,0,readsize);
                                                LCF.addToHash(md,new String(buffer,0,readsize));
                                                totalMoved += readsize;
                                        }
                                        
                                        charLength = totalMoved;
                                        hashValue = LCF.getHashValue(md);
                                }
                                finally
                                {
                                        outWriter.close();
                                }

                                // Now, create the input stream.
                                // Save the file name
                                file = outfile;

                        }
                        catch (Throwable e)
                        {
                                // Delete the temp file we created on any error condition
                                // outfile.delete();
                                LCF.deleteFile(outfile);
                                if (e instanceof Error)
                                        throw (Error)e;
                                if (e instanceof RuntimeException)
                                        throw (RuntimeException)e;
                                if (e instanceof Exception)
                                        throw (Exception)e;
                                throw new Exception("Unexpected throwable: "+e.getMessage(),e);
                        }
                }
                catch (InterruptedIOException e)
                {
                        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
                }
                catch (Exception e)
                {
                        throw new LCFException("Cannot write temporary file: "+e.getMessage(),e,LCFException.GENERAL_ERROR);
                }

        }

        /** Construct from an existing temporary fle.
        *@param tempFile is the existing temporary file, encoded in utf-8.
        */
        public TempFileCharacterInput(File tempFile)
        {
                super();
                file = tempFile;
                LCF.addFile(file);
                // deleteOnExit() causes memory leakage; better to leak files on hard shutdown than memory.
                // file.deleteOnExit();
        }

        protected TempFileCharacterInput()
        {
                super();
        }

        /** Open a Utf8 stream directly from the backing file */
        public InputStream getUtf8Stream()
                throws LCFException
        {
                if (file != null)
                {
                        try
                        {
                                return new FileInputStream(file);
                        }
                        catch (FileNotFoundException e)
                        {
                                throw new LCFException("No such file: "+e.getMessage(),e,LCFException.GENERAL_ERROR);
                        }
                }
                return null;
        }

        protected void openStream()
                throws LCFException
        {
                try
                {
                        // Open the file and create a stream.
                        InputStream binaryStream = new FileInputStream(file);
                        stream = new InputStreamReader(binaryStream,"utf-8");
                }
                catch (FileNotFoundException e)
                {
                        throw new LCFException("Can't create stream: "+e.getMessage(),e,LCFException.GENERAL_ERROR);
                }
                catch (UnsupportedEncodingException e)
                {
                        throw new LCFException("Can't create stream: "+e.getMessage(),e,LCFException.GENERAL_ERROR);
                }
        }

        /** Transfer to a new object; this causes the current object to become "already discarded" */
        public CharacterInput transfer()
        {
                // Create a new TempFileCharacterInput object, and fill it with our current stuff
                TempFileCharacterInput rval = new TempFileCharacterInput();
                rval.file = file;
                rval.stream = stream;
                rval.charLength = charLength;
                rval.hashValue = hashValue;
                file = null;
                stream = null;
                charLength = -1L;
                hashValue = null;
                return rval;
        }

        public void discard()
                throws LCFException
        {
                super.discard();
                // Delete the underlying file
                if (file != null)
                {
                        LCF.deleteFile(file);
                        file = null;
                }
        }

        /** Calculate the datum's length in characters */
        protected void calculateLength()
                throws LCFException
        {
                scanFile();
        }
        
        /** Calculate the datum's hash value */
        protected void calculateHashValue()
                throws LCFException
        {
                scanFile();
        }

        private void scanFile()
                throws LCFException
        {
                // Scan the file in order to figure out the hash value and the character length
                try
                {
                        // Open the file and create a stream.
                        InputStream binaryStream = new FileInputStream(file);
                        Reader reader = new InputStreamReader(binaryStream,"utf-8");
                        try
                        {
                                // Set up hash digest and character length counter before we start anything.
                                java.security.MessageDigest md = LCF.startHash();
                                char[] buffer = new char[CHUNK_SIZE];
                                long totalMoved = 0;
                                while (true)
                                {
                                        int moveAmount = CHUNK_SIZE;
                                        // Read character data in 64K chunks
                                        int readsize = reader.read(buffer,0,moveAmount);
                                        if (readsize == -1)
                                                break;
                                        LCF.addToHash(md,new String(buffer,0,readsize));
                                        totalMoved += readsize;
                                }
                                        
                                charLength = totalMoved;
                                hashValue = LCF.getHashValue(md);
                        }
                        finally
                        {
                                reader.close();
                        }
                }
                catch (InterruptedIOException e)
                {
                        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
                }
                catch (IOException e)
                {
                        throw new LCFException("Can't scan file: "+e.getMessage(),e,LCFException.GENERAL_ERROR);
                }
        }
        
}
