/* $Id: TempFileInput.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class represents a temporary file data input
* stream.  Call the "done" method to clean up the
* file when done.
* NOTE: The implied flow of this method is to be handled
* a file that has already been created by some means.  The
* file must be a dedicated temporary file, which can be
* destroyed when the data has been used.
*/
public class TempFileInput extends BinaryInput
{
  public static final String _rcsid = "@(#)$Id: TempFileInput.java 988245 2010-08-23 18:39:35Z kwright $";

  protected File file;

  protected final static int CHUNK_SIZE = 65536;

  /** Construct from an input stream.
  * This will also create a temporary, backing file.
  *@param is is the input stream to use to construct the temporary file.
  */
  public TempFileInput(InputStream is)
    throws ManifoldCFException
  {
    this(is,-1L);
  }

  /** Construct from a length-delimited input stream.
  *@param is is the input stream.
  *@param length is the maximum number of bytes to transfer, or -1 if no limit.
  */
  public TempFileInput(InputStream is, long length)
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
        FileOutputStream outStream = new FileOutputStream(outfile);
        try
        {
          byte[] buffer = new byte[CHUNK_SIZE];
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
            // Read binary data in 64K chunks
            int readsize = is.read(buffer,0,moveAmount);
            if (readsize == -1)
              break;
            outStream.write(buffer,0,readsize);
            totalMoved += readsize;
          }
          // System.out.println(" Moved "+Long.toString(totalMoved));
        }
        finally
        {
          outStream.close();
        }

        // Now, create the input stream.
        // Save the file name
        file = outfile;
        this.length = file.length();

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
      throw new ManifoldCFException("Cannot write temporary file",e,ManifoldCFException.GENERAL_ERROR);
    }

  }

  /** Construct from an existing temporary fle.
  *@param tempFile is the existing temporary file.
  */
  public TempFileInput(File tempFile)
  {
    super();
    file = tempFile;
    ManifoldCF.addFile(file);
    // deleteOnExit() causes memory leakage; better to leak files on hard shutdown than memory.
    // file.deleteOnExit();
  }

  protected TempFileInput()
  {
    super();
  }

  /** Transfer to a new object; this causes the current object to become "already discarded" */
  public BinaryInput transfer()
  {
    TempFileInput rval = new TempFileInput();
    rval.file = file;
    rval.stream = stream;
    rval.length = length;
    file = null;
    stream = null;
    length = -1L;
    return rval;
  }

  public void discard()
    throws ManifoldCFException
  {
    super.discard();
    if (file != null)
    {
      ManifoldCF.deleteFile(file);
      file = null;
    }
  }

  protected void openStream()
    throws ManifoldCFException
  {
    try
    {
      // Open the file and create a stream.
      stream = new FileInputStream(file);
    }
    catch (FileNotFoundException e)
    {
      throw new ManifoldCFException("Can't create stream: "+e.getMessage(),e,ManifoldCFException.GENERAL_ERROR);
    }
  }

  protected void calculateLength()
    throws ManifoldCFException
  {
    this.length = file.length();
  }

}
