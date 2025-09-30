/* $Id: RMILocalClientSocketFactory.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.common.DCTM;

import java.rmi.server.*;
import java.net.*;
import java.io.IOException;

/** This factory mints client-side sockets.  I've created one so the $%^&* rmi world doesn't attempt
* to connect to anything other than localhost (127.0.0.1).
*/
public class RMILocalClientSocketFactory implements RMIClientSocketFactory, java.io.Serializable
{
  public static final String _rcsid = "@(#)$Id: RMILocalClientSocketFactory.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Constructor */
  public RMILocalClientSocketFactory()
  {
  }

  /** The method that mints a socket of the right kind.
  */
  public Socket createSocket(String host, int port)
    throws IOException
  {
    return new LocalClientSocket(port);
  }

  /** The contract makes us implement equals and hashcode */
  public boolean equals(Object o)
  {
    return (o instanceof RMILocalClientSocketFactory);
  }

  /** Hashcode consistent with equals() */
  public int hashCode()
  {
    // All classes of this kind have the same number (randomly picked)
    return 258474;
  }


}
