/* $Id: LocalClientSocket.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.common.filenet;

import java.rmi.server.*;
import java.net.*;
import java.io.IOException;

/** This class wraps Socket and does not permit it to be directed to connect anywhere other than to localhost.
*/
public class LocalClientSocket extends Socket
{
  public static final String _rcsid = "@(#)$Id: LocalClientSocket.java 988245 2010-08-23 18:39:35Z kwright $";

  protected static InetAddress loopbackAddress;

  static
  {
    try
    {
      loopbackAddress = InetAddress.getByAddress(new byte[]{127,0,0,1});
    }
    catch (UnknownHostException e)
    {
      e.printStackTrace();
    }
  }

  protected int currentPort;

  /** Constructor */
  public LocalClientSocket(int port)
    throws IOException
  {
    super(loopbackAddress,port);
    currentPort = port;
  }

  public void connect(SocketAddress endpoint)
    throws IOException
  {
    int thisPort = currentPort;
    if (endpoint instanceof InetSocketAddress)
      thisPort = ((InetSocketAddress)endpoint).getPort();
    endpoint = new InetSocketAddress(loopbackAddress,thisPort);
    super.connect(endpoint);
  }

  public void connect(SocketAddress endpoint, int timeout)
    throws IOException
  {
    int thisPort = currentPort;
    if (endpoint instanceof InetSocketAddress)
      thisPort = ((InetSocketAddress)endpoint).getPort();
    endpoint = new InetSocketAddress(loopbackAddress,thisPort);
    super.connect(endpoint,timeout);
  }

}
