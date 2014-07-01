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
import java.nio.charset.StandardCharsets;

import org.apache.manifoldcf.core.system.ManifoldCF;

/** This class represents a temporary file character input
* stream.  Call the "done" method to clean up the
* file when done.
* NOTE: The implied flow of this method is to be handed
* a file that has already been created by some means.  The
* file must be a dedicated temporary file, which can be
* destroyed when the data has been used.  However, this class can also
* buffer data in memory if the data is not too large (that is, less than a
* supplied cutoff value).
*/
public class TempFileCharacterInput extends CharacterInput
{
  public static final String _rcsid = "@(#)$Id: TempFileCharacterInput.java 988245 2010-08-23 18:39:35Z kwright $";

  protected File file;
  protected byte[] inMemoryBuffer;

  protected final static int CHUNK_SIZE = 65536;
  protected final static int DEFAULT_MAX_MEM_SIZE = 8192;
  
  /** Construct from a non-length-delimited reader.
  *@param is is a reader to transfer from, to the end of the data.  This will, as a side effect, also calculate the character length
  *          and hash value for the data.
  */
  public TempFileCharacterInput(Reader is)
    throws ManifoldCFException, IOException
  {
    this(is,-1L);
  }

  /** Construct from a length-delimited reader.
  *@param is is a reader to transfer from, to the end of the data.  This will, as a side effect, also calculate the character length
  *          and hash value for the data.
  *@param length is the length limit to transfer, or -1 if no limit
  */
  public TempFileCharacterInput(Reader is, long length)
    throws ManifoldCFException, IOException
  {
    this(is,length,DEFAULT_MAX_MEM_SIZE);
  }

  /** Construct from a length-delimited reader.
  *@param is is a reader to transfer from, to the end of the data.  This will, as a side effect, also calculate the character length
  *          and hash value for the data.
  *@param length is the length limit to transfer, or -1 if no limit
  *@param maxInMemoryLength is the maximum size to keep in memory, before using a backing File object.  The amount possibly
  *        saved in memory will be guaranteed less than this size.
  */
  public TempFileCharacterInput(Reader is, long length, int maxInMemoryLength)
    throws ManifoldCFException, IOException
  {
    super();
    

    // Before we do anything else, we read the first chunk.  This will allow
    // us to determine if we're going to buffer the data in memory or not.  However,
    // it may need to be read in chunks, since there's no guarantee it will come in
    // in the size requested.
    int chunkSize = CHUNK_SIZE;

    char[] buffer = new char[chunkSize];
    int chunkTotal = 0;
    boolean eofSeen = false;
    while (true)
    {
      int chunkAmount;
      if (length == -1L || length > chunkSize)
        chunkAmount = chunkSize-chunkTotal;
      else
      {
        chunkAmount = (int)(length-chunkTotal);
        eofSeen = true;
      }
      if (chunkAmount == 0)
        break;
      int readsize = is.read(buffer,chunkTotal,chunkAmount);
      if (readsize == -1)
      {
        eofSeen = true;
        break;
      }
      chunkTotal += readsize;
    }
    
    // Set up hash digest, and calculate the initial hash.
    java.security.MessageDigest md = ManifoldCF.startHash();
    String chunkString = new String(buffer,0,chunkTotal);
    ManifoldCF.addToHash(md,chunkString);

    // In order to compute the byte length, we need to convert to a byte array, which is
    // also our final form for in-memory storage.  But we don't want to  do the work
    // unless there's a chance it will be needed.
    byte[] byteBuffer;
    if (eofSeen)
      byteBuffer = chunkString.getBytes(StandardCharsets.UTF_8);
    else
      byteBuffer = null;

    if (eofSeen && byteBuffer.length <= maxInMemoryLength)
    {
      // Buffer locally; don't create a temp file
      file = null;
      inMemoryBuffer = byteBuffer;
      charLength = chunkTotal;
      hashValue = ManifoldCF.getHashValue(md);
    }
    else
    {
      inMemoryBuffer = null;
      // Create a temporary file!
      long totalMoved = 0;
      
      // Create a temporary file to put the stuff in
      File outfile;
      try
      {
        outfile = File.createTempFile("_MC_","");
      }
      catch (IOException e)
      {
        handleIOException(e,"creating backing file");
        outfile = null;
      }
      try
      {
        // Register the file for autodeletion, using our infrastructure.
        ManifoldCF.addFile(outfile);
        // deleteOnExit() causes memory leakage!
        // outfile.deleteOnExit();

        FileOutputStream outStream;
        OutputStreamWriter outWriter;
        try
        {
          outStream = new FileOutputStream(outfile);
          // Create a Writer corresponding to the file output stream, and encode using utf-8
          outWriter = new OutputStreamWriter(outStream,StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
          handleIOException(e,"opening backing file");
          outStream = null;
          outWriter = null;
        }
        try
        {
          //  Transfor what we've already read.
          try
          {
            outWriter.write(buffer,0,chunkTotal);
          }
          catch (IOException e)
          {
            handleIOException(e,"writing backing file");
          }
          totalMoved += chunkTotal;
          // Now, transfer the remainder
          while (true)
          {
            int moveAmount;
            if (length == -1L || length-totalMoved > chunkSize)
              moveAmount = chunkSize;
            else
              moveAmount = (int)(length-totalMoved);
            if (moveAmount == 0)
              break;
            // Read character data in 64K chunks
            int readsize = is.read(buffer,0,moveAmount);
            if (readsize == -1)
              break;
            try
            {
              outWriter.write(buffer,0,readsize);
            }
            catch (IOException e)
            {
              handleIOException(e,"writing backing file");
            }
            ManifoldCF.addToHash(md,new String(buffer,0,readsize));
            totalMoved += readsize;
          }

        }
        finally
        {
          try
          {
            outWriter.close();
          }
          catch (IOException e)
          {
            handleIOException(e,"closing backing file");
          }
        }

        // Now, create the input stream.
        // Save the file name
        file = outfile;
        charLength = totalMoved;
        hashValue = ManifoldCF.getHashValue(md);

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
        if (e instanceof ManifoldCFException)
          throw (ManifoldCFException)e;
        if (e instanceof IOException)
          throw (IOException)e;
        throw new RuntimeException("Unexpected throwable of type "+e.getClass().getName()+": "+e.getMessage(),e);
      }
    }
    
  }

  /** Construct from an existing temporary fle.
  *@param tempFile is the existing temporary file, encoded in utf-8.
  */
  public TempFileCharacterInput(File tempFile)
  {
    super();
    inMemoryBuffer = null;
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
    else if (inMemoryBuffer != null)
    {
      return new ByteArrayInputStream(inMemoryBuffer);
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
    else if (inMemoryBuffer != null)
      return inMemoryBuffer.length;
    return 0L;
  }

  @Override
  protected void openStream()
    throws ManifoldCFException
  {
    if (file != null)
    {
      try
      {
        // Open the file and create a stream.
        InputStream binaryStream = new FileInputStream(file);
        stream = new InputStreamReader(binaryStream, StandardCharsets.UTF_8);
      }
      catch (FileNotFoundException e)
      {
        throw new ManifoldCFException("Can't create stream: "+e.getMessage(),e,ManifoldCFException.GENERAL_ERROR);
      }
    }
    else if (inMemoryBuffer != null)
    {
      stream = new InputStreamReader(new ByteArrayInputStream(inMemoryBuffer),StandardCharsets.UTF_8);
    }
  }

  /** Transfer to a new object; this causes the current object to become "already discarded" */
  @Override
  public CharacterInput transfer()
  {
    // Create a new TempFileCharacterInput object, and fill it with our current stuff
    TempFileCharacterInput rval = new TempFileCharacterInput();
    rval.file = file;
    rval.inMemoryBuffer = inMemoryBuffer;
    rval.stream = stream;
    rval.charLength = charLength;
    rval.hashValue = hashValue;
    file = null;
    inMemoryBuffer = null;
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
      InputStream binaryStream;
      if (file != null)
        binaryStream = new FileInputStream(file);
      else if (inMemoryBuffer != null)
        binaryStream = new ByteArrayInputStream(inMemoryBuffer);
      else
        binaryStream = null;
      Reader reader = new InputStreamReader(binaryStream,StandardCharsets.UTF_8);
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
    catch (IOException e)
    {
      handleIOException(e,"scanning file");
    }
  }

}
