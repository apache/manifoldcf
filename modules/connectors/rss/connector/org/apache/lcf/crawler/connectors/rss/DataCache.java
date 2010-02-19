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
package org.apache.lcf.crawler.connectors.rss;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.Logging;
import org.apache.lcf.crawler.system.LCF;
import java.util.*;
import java.io.*;

/** This class is a cache of a specific URL's data.  It's fetched early and kept,
* so that (1) an accurate data length can be found, and (2) we can compute a version
* checksum.
*/
public class DataCache
{
  public static final String _rcsid = "@(#)$Id$";

  // Hashmap containing the cache
  protected HashMap cacheData = new HashMap();

  /** Constructor.
  */
  public DataCache()
  {
  }


  /** Add binary data entry into the cache.  Does NOT close the input stream when done!
  *@param documentIdentifier is the document identifier (url).
  *@param dataStream is the data stream.
  *@return the checksum value.
  */
  public long addData(IVersionActivity activities, String documentIdentifier, InputStream dataStream)
    throws LCFException, ServiceInterruption
  {
    // Create a temporary file; that's what we will cache
    try
    {
      File tempFile = File.createTempFile("_rsscache_","tmp");
      try
      {
        // Causes memory leaks if left around; there's no way to release
        // the record specifying that the file should be deleted, even
        // after it's removed.  So disable this and live with the occasional
        // dangling file left as a result of shutdown or error. :-(
        // tempFile.deleteOnExit();
        LCF.addFile(tempFile);

        // Transfer data to temporary file
        long checkSum = 0L;
        OutputStream os = new FileOutputStream(tempFile);
        try
        {
          byte[] byteArray = new byte[65536];
          while (true)
          {
            int amt;
            try
            {
              amt = dataStream.read(byteArray,0,byteArray.length);
            }
            catch (java.net.SocketTimeoutException e)
            {
              Logging.connectors.warn("RSS: Socket timeout exception reading socket stream: "+e.getMessage(),e);
              long currentTime = System.currentTimeMillis();
              throw new ServiceInterruption("Read timeout: "+e.getMessage(),e,currentTime + 300000L,
                currentTime + 12 * 60 * 60000L,-1,false);
            }
            catch (org.apache.commons.httpclient.ConnectTimeoutException e)
            {
              Logging.connectors.warn("RSS: Connect timeout exception reading socket stream: "+e.getMessage(),e);
              long currentTime = System.currentTimeMillis();
              throw new ServiceInterruption("Read timeout: "+e.getMessage(),e,currentTime + 300000L,
                currentTime + 12 * 60 * 60000L,-1,false);
            }
            catch (InterruptedIOException e)
            {
              throw new LCFException("Interrupted: "+e.getMessage(),LCFException.INTERRUPTED);
            }
            catch (IOException e)
            {
              Logging.connectors.warn("RSS: IO exception reading socket stream: "+e.getMessage(),e);
              long currentTime = System.currentTimeMillis();
              throw new ServiceInterruption("Read timeout: "+e.getMessage(),e,currentTime + 300000L,
                currentTime + 12 * 60 * 60000L,-1,false);
            }
            if (amt == -1)
              break;
            int i = 0;
            while (i < amt)
            {
              byte x = byteArray[i++];
              long bytevalue = (long)x;
              checkSum = (checkSum << 5) ^ (checkSum >> 3) ^ (bytevalue << 2) ^ (bytevalue >> 3);
            }

            os.write(byteArray,0,amt);
            // Before we go 'round again, do a check
            activities.checkJobStillActive();
          }
        }
        finally
        {
          os.close();
        }

        synchronized(this)
        {
          deleteData(documentIdentifier);
          cacheData.put(documentIdentifier,tempFile);
          return checkSum;
        }

      }
      catch (IOException e)
      {
        LCF.deleteFile(tempFile);
        throw e;
      }
      catch (ServiceInterruption e)
      {
        LCF.deleteFile(tempFile);
        throw e;
      }
      catch (Error e)
      {
        LCF.deleteFile(tempFile);
        throw e;
      }
    }
    catch (java.net.SocketTimeoutException e)
    {
      throw new LCFException("Socket timeout exception creating temporary file: "+e.getMessage(),e);
    }
    catch (org.apache.commons.httpclient.ConnectTimeoutException e)
    {
      throw new LCFException("Socket connect timeout exception creating temporary file: "+e.getMessage(),e);
    }
    catch (InterruptedIOException e)
    {
      throw new LCFException("Interrupted: "+e.getMessage(),LCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new LCFException("IO exception creating temporary file: "+e.getMessage(),e);
    }
  }

  /** Fetch binary data length.
  *@param documentIdentifier is the document identifier.
  *@return the length.
  */
  public synchronized long getDataLength(String documentIdentifier)
    throws LCFException
  {
    File f = (File)cacheData.get(documentIdentifier);
    if (f == null)
      return 0L;
    return f.length();
  }

  /** Fetch binary data entry from the cache.
  *@param documentIdentifier is the document identifier (url).
  *@return a binary data stream.
  */
  public synchronized InputStream getData(String documentIdentifier)
    throws LCFException
  {
    File f = (File)cacheData.get(documentIdentifier);
    if (f == null)
      return null;
    try
    {
      return new FileInputStream(f);
    }
    catch (IOException e)
    {
      throw new LCFException("IO exception getting data length: "+e.getMessage(),e);
    }
  }

  /** Delete specified item of data.
  *@param documentIdentifier is the document identifier (url).
  */
  public synchronized void deleteData(String documentIdentifier)
  {
    File f = (File)cacheData.get(documentIdentifier);
    cacheData.remove(documentIdentifier);
    if (f != null)
    {
      LCF.deleteFile(f);
    }
  }

}
