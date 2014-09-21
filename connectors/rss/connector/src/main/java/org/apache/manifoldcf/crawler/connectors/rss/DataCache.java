/* $Id: DataCache.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.rss;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import java.util.*;
import java.io.*;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.client.RedirectException;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.NoHttpResponseException;
import org.apache.http.HttpException;

/** This class is a cache of a specific URL's data.  It's fetched early and kept,
* so that (1) an accurate data length can be found, and (2) we can compute a version
* checksum.
*/
public class DataCache
{
  public static final String _rcsid = "@(#)$Id: DataCache.java 988245 2010-08-23 18:39:35Z kwright $";

  // Hashmap containing the cache
  protected Map<String,DocumentData> cacheData = new HashMap<String,DocumentData>();

  /** Constructor.
  */
  public DataCache()
  {
  }


  /** Add binary data entry into the cache.  Does NOT close the input stream when done!
  *@param documentIdentifier is the document identifier (url).
  *@param contentType is the content type for the data.
  *@param dataStream is the data stream.
  *@return the checksum value.
  */
  public long addData(IProcessActivity activities, String documentIdentifier, String contentType, InputStream dataStream)
    throws ManifoldCFException, ServiceInterruption
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
        ManifoldCF.addFile(tempFile);

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
            catch (ConnectTimeoutException e)
            {
              Logging.connectors.warn("RSS: Connect timeout exception reading socket stream: "+e.getMessage(),e);
              long currentTime = System.currentTimeMillis();
              throw new ServiceInterruption("Read timeout: "+e.getMessage(),e,currentTime + 300000L,
                currentTime + 12 * 60 * 60000L,-1,false);
            }
            catch (InterruptedIOException e)
            {
              throw new ManifoldCFException("Interrupted: "+e.getMessage(),ManifoldCFException.INTERRUPTED);
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

        deleteData(documentIdentifier);
        
        synchronized(this)
        {
          cacheData.put(documentIdentifier,new DocumentData(tempFile,contentType));
        }
        
        return checkSum;
      }
      catch (IOException e)
      {
        ManifoldCF.deleteFile(tempFile);
        throw e;
      }
      catch (ServiceInterruption e)
      {
        ManifoldCF.deleteFile(tempFile);
        throw e;
      }
      catch (Error e)
      {
        ManifoldCF.deleteFile(tempFile);
        throw e;
      }
    }
    catch (java.net.SocketTimeoutException e)
    {
      throw new ManifoldCFException("Socket timeout exception creating temporary file: "+e.getMessage(),e);
    }
    catch (ConnectTimeoutException e)
    {
      throw new ManifoldCFException("Socket connect timeout exception creating temporary file: "+e.getMessage(),e);
    }
    catch (InterruptedIOException e)
    {
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO exception creating temporary file: "+e.getMessage(),e);
    }
  }

  /** Fetch binary data length.
  *@param documentIdentifier is the document identifier.
  *@return the length.
  */
  public long getDataLength(String documentIdentifier)
    throws ManifoldCFException
  {
    DocumentData f;
    synchronized (this)
    {
      f = cacheData.get(documentIdentifier);
    }
    if (f == null)
      return 0L;
    return f.getData().length();
  }

  /** Fetch binary data entry from the cache.
  *@param documentIdentifier is the document identifier (url).
  *@return a binary data stream.
  */
  public InputStream getData(String documentIdentifier)
    throws ManifoldCFException
  {
    DocumentData f;
    synchronized (this)
    {
      f = cacheData.get(documentIdentifier);
    }
    if (f == null)
      return null;
    try
    {
      return new FileInputStream(f.getData());
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO exception getting data length: "+e.getMessage(),e);
    }
  }

  /** Get the content type.
  *@param documentIdentifier is the document identifier.
  *@return the content type, or null if there is none.
  */
  public String getContentType(String documentIdentifier)
  {
    DocumentData dd;
    synchronized (this)
    {
      dd = cacheData.get(documentIdentifier);
    }
    if (dd == null)
      return null;
    return dd.getContentType();
  }

  /** Delete specified item of data.
  *@param documentIdentifier is the document identifier (url).
  */
  public void deleteData(String documentIdentifier)
  {
    DocumentData f;
    synchronized (this)
    {
      f = cacheData.remove(documentIdentifier);
    }
    if (f != null)
    {
      ManifoldCF.deleteFile(f.getData());
    }
  }

  // Protected classes

  /** This class represents everything we need to know about a document that's getting passed from the
  * getDocumentVersions() phase to the processDocuments() phase.
  */
  protected static class DocumentData
  {
    /** The cache file for the data */
    protected File data;
    /** The content-type header value */
    protected String contentType;

    /** Constructor. */
    public DocumentData(File data, String contentType)
    {
      this.data = data;
      this.contentType = contentType;
    }

    /** Get the data */
    public File getData()
    {
      return data;
    }

    /** Get the contentType */
    public String getContentType()
    {
      return contentType;
    }

  }

}
