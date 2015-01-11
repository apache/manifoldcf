/* $Id: IThrottledConnection.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import java.io.*;

/** This interface represents an established connection to a URL.
*/
public interface IThrottledConnection
{
  public static final String _rcsid = "@(#)$Id: IThrottledConnection.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Status code for fetch: OK */
  public static final int STATUS_OK = 0;
  /** Status code for fetch: Static error; retries won't help, overall access to site in question */
  public static final int STATUS_SITEERROR = 1;
  /** Status code for fetch: Static error; retries won't help, individual page access failed */
  public static final int STATUS_PAGEERROR = 2;
  /** Status code for fetch: No change. */
  public static final int STATUS_NOCHANGE = 3;

  // Issue codes.
  public static final int FETCH_NOT_TRIED = -1;
  public static final int FETCH_CIRCULAR_REDIRECT = -100;
  public static final int FETCH_BAD_URI = -101;
  public static final int FETCH_SEQUENCE_ERROR = -102;
  public static final int FETCH_IO_ERROR = -103;
  public static final int FETCH_UNKNOWN_ERROR = -999;

  /** Begin the fetch process.
  * @param fetchType is a short descriptive string describing the kind of fetch being requested.  This
  *        is used solely for logging purposes.
  */
  public void beginFetch(String fetchType)
    throws ManifoldCFException, ServiceInterruption;

  /** Execute the fetch and get the return code.  This method uses the
  * standard logging mechanism to keep track of the fetch attempt.  It also
  * signals the following three conditions: ServiceInterruption (if a dynamic
  * error occurs), OK, or a static error code (for a condition where retry is
  * not likely to be helpful).  The actual HTTP error code is NOT returned by
  * this method.
  * @param protocol is the protocol to use to perform the access, e.g. "http"
  * @param port is the port to use to perform the access, where -1 means "use the default"
  * @param urlPath is the path part of the url, e.g. "/robots.txt"
  * @param userAgent is the value of the userAgent header to use.
  * @param from is the value of the from header to use.
  * @return the status code: success, static error, or dynamic error.
  */
  public int executeFetch(String protocol, int port, String urlPath, String userAgent, String from,
    String lastETag, String lastModified)
    throws ManifoldCFException, ServiceInterruption;

  /** Get the http response code.
  *@return the response code.  This is either an HTTP response code, or one of the codes above.
  */
  public int getResponseCode()
    throws ManifoldCFException, ServiceInterruption;

  /** Get the response input stream.  It is the responsibility of the caller
  * to close this stream when done.
  */
  public InputStream getResponseBodyStream()
    throws ManifoldCFException, ServiceInterruption;

  /** Get a specified response header, if it exists.
  *@param headerName is the name of the header.
  *@return the header value, or null if it doesn't exist.
  */
  public String getResponseHeader(String headerName)
    throws ManifoldCFException, ServiceInterruption;

  /** Done with the fetch.  Call this when the fetch has been completed.  A log entry will be generated
  * describing what was done.
  */
  public void doneFetch(IProcessActivity activities)
    throws ManifoldCFException;

  /** Close the connection.  Call this to end this server connection.
  */
  public void close()
    throws ManifoldCFException;
}
