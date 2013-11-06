/* $Id: TempFileCharacterInput.java 988245 2010-08-23 18:39:35Z kwright $ */

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
  public static final String _rcsid = "@(#)$Id: TempFileCharacterInput.java 988245 2010-08-23 18:39:35Z kwright $";

  protected File file;

  protected final static int CHUNK_SIZE = 65536;

  /** Construct from a non-length-delimited reader.
  *@param is is a reader to transfer from, to the end of the data.  This will, as a side effect, also calculate the character length
  *          and hash value for the data.
  */
  public TempFileCharacterInput(Reader is)
    throws ManifoldCFException
  {
    this(is,-1L);
  }

  /** Construct from a length-delimited reader.
  *@param is is a reader to transfer from, to the end of the data.  This will, as a side effect, also calculate the character length
  *          and hash value for the data.
  *@param length is the length limit to transfer, or -1 if no limit
  */
  public TempFileCharacterInput(Reader is, long length)
    throws ManifoldCFException
  {
    super();
    try
    {
      // Create a temporary file to put the stuff in
      File outfile = File.createTempFile("_MC_","");
      try
      {
        // Register the file for autodeletion, using our infrastructure.
        ManifoldCF.addFile(outfile);
        // deleteOnExit() causes memory leakage!
        // outfile.deleteOnExit();

        // Set up hash digest and character length counter before we start anything.
        java.security.MessageDigest md = ManifoldCF.startHash();

        FileOutputStream outStream = new FileOutputStream(outfile);
        // Create a Writer corresponding to the file output stream, and encode using utf-8
        OutputStreamWriter outWriter = new OutputStreamWriter(outStream,"utf-8");
        try
        {
          char[] buffer = new char[CHUNK_SIZE];
          long totalMoved = 0;
          while (true)
          {
            int moveAmount;
            if (length == -1L || length-totalMoved > CHUNK_SIZE)
              moveAmount = CHUNK_SIZE;
            else
              moveAmount = (int)(length-totalMoved);
            if (moveAmount == 0)
              break;
            // Read character data in 64K chunks
            int readsize = is.read(buffer,0,moveAmount);
            if (readsize == -1)
              break;
            outWriter.write(buffer,0,readsize);
            ManifoldCF.addToHash(md,new String(buffer,0,readsize));
            totalMoved += readsize;
          }

          charLength = totalMoved;
          hashValue = ManifoldCF.getHashValue(md);
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
        ManifoldCF.deleteFile(outfile);
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
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (Exception e)
    {
      throw new ManifoldCFException("Cannot write temporary file: "+e.getMessage(),e,ManifoldCFException.GENERAL_ERROR);
    }

  }

  /** Construct from an existing temporary fle.
  *@param tempFile is the existing temporary file, encoded in utf-8.
  */
  public TempFileCharacterInput(File tempFile)
  {
    super();
    file = tempFile;
    ManifoldCF.addFile(file);
    // deleteOnExit() causes memory leakage; better to leak files on hard shutdown than memory.
    // file.deleteOnExit();
  }

  protected TempFileCharacterInput()
  {
    super();
  }

  /** Open a Utf8 stream directly from the backing file */
  @Override
  public InputStream getUtf8Stream()
    throws ManifoldCFException
  {
    if (file != null)
    {
      try
      {
        return new FileInputStream(file);
      }
      catch (FileNotFoundException e)
      {
        throw new ManifoldCFException("No such file: "+e.getMessage(),e,ManifoldCFException.GENERAL_ERROR);
      }
    }
    return null;
  }

  /** Get binary UTF8 stream length directly */
  @Override
  public long getUtf8StreamLength()
    throws ManifoldCFException
  {
    if (file != null)
      return file.length();
    return 0L;
  }

  @Override
  protected void openStream()
    throws ManifoldCFException
  {
    try
    {
      // Open the file and create a stream.
      InputStream binaryStream = new FileInputStream(file);
      stream = new InputStreamReader(binaryStream,"utf-8");
    }
    catch (FileNotFoundException e)
    {
      throw new ManifoldCFException("Can't create stream: "+e.getMessage(),e,ManifoldCFException.GENERAL_ERROR);
    }
    catch (UnsupportedEncodingException e)
    {
      throw new ManifoldCFException("Can't create stream: "+e.getMessage(),e,ManifoldCFException.GENERAL_ERROR);
    }
  }

  /** Transfer to a new object; this causes the current object to become "already discarded" */
  @Override
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

  @Override
  public void discard()
    throws ManifoldCFException
  {
    super.discard();
    // Delete the underlying file
    if (file != null)
    {
      ManifoldCF.deleteFile(file);
      file = null;
    }
  }

  /** Calculate the datum's length in characters */
  @Override
  protected void calculateLength()
    throws ManifoldCFException
  {
    scanFile();
  }

  /** Calculate the datum's hash value */
  @Override
  protected void calculateHashValue()
    throws ManifoldCFException
  {
    scanFile();
  }

  private void scanFile()
    throws ManifoldCFException
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
        java.security.MessageDigest md = ManifoldCF.startHash();
        char[] buffer = new char[CHUNK_SIZE];
        long totalMoved = 0;
        while (true)
        {
          int moveAmount = CHUNK_SIZE;
          // Read character data in 64K chunks
          int readsize = reader.read(buffer,0,moveAmount);
          if (readsize == -1)
            break;
          ManifoldCF.addToHash(md,new String(buffer,0,readsize));
          totalMoved += readsize;
        }

        charLength = totalMoved;
        hashValue = ManifoldCF.getHashValue(md);
      }
      finally
      {
        reader.close();
      }
    }
    catch (InterruptedIOException e)
    {
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("Can't scan file: "+e.getMessage(),e,ManifoldCFException.GENERAL_ERROR);
    }
  }

}
