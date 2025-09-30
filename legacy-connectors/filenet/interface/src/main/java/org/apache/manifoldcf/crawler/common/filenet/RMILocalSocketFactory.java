/* $Id: RMILocalSocketFactory.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class is the main server class, which gets run to start the rmi service that talks to Filenet.
*/
public class RMILocalSocketFactory implements RMIServerSocketFactory
{
  public static final String _rcsid = "@(#)$Id: RMILocalSocketFactory.java 988245 2010-08-23 18:39:35Z kwright $";

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

  /** Create a socket attached to the specified port.  0 means an anonymous port. */
  public ServerSocket createServerSocket(int port)
    throws IOException
  {
    return new LocalServerSocket(port);
  }

  /** The contract makes us implement equals and hashcode */
  public boolean equals(Object o)
  {
    return (o instanceof RMILocalSocketFactory);
  }

  /** Hashcode consistent with equals() */
  public int hashCode()
  {
    // All classes of this kind have the same number (randomly picked)
    return 258476;
  }

  /** This is a localhost-bound implementation of ServerSocket */
  protected static class LocalServerSocket extends ServerSocket
  {
    protected int currentPort;

    /** Constructor.  We only use the one, so the rest are immaterial. */
    public LocalServerSocket(int port)
      throws IOException
    {
      super(port);
      currentPort = port;
    }

    /** Override the bind operation, to make sure we only bind to localhost */
    public void bind(java.net.SocketAddress endpoint)
      throws IOException
    {
      int thisPort = currentPort;
      if (endpoint instanceof InetSocketAddress)
        thisPort = ((InetSocketAddress)endpoint).getPort();
      endpoint = new InetSocketAddress(loopbackAddress,thisPort);
      super.bind(endpoint);
    }

    /** Override the bind operation, to make sure we only bind to localhost */
    public void bind(java.net.SocketAddress endpoint, int backlog)
      throws IOException
    {
      int thisPort = currentPort;
      if (endpoint instanceof InetSocketAddress)
        thisPort = ((InetSocketAddress)endpoint).getPort();
      endpoint = new InetSocketAddress(loopbackAddress,thisPort);
      super.bind(endpoint,backlog);
    }
  }
}
