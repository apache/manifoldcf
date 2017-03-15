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
package org.apache.manifoldcf.core.common;

import org.apache.manifoldcf.core.interfaces.*;
import javax.net.ssl.SSLSocketFactory;
import java.security.*;
import java.io.*;
import java.net.Socket;
import java.net.InetAddress;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLContext;

/** This SSLSocketFactory is meant to be instantiated by Java's LDAP code.  It has
* to be instantiated by name, using the default constructor, so its functionality is quite
* limited.  It really has little choice other than to trust the certificates from the server.
*/
public class LDAPSSLSocketFactory extends SSLSocketFactory
{
  /** This is the implicit way to pass in a socket factory producer */
  protected static final ThreadLocal<ISSLSocketFactoryProducer> sslSocketFactoryProducer = new ThreadLocal<ISSLSocketFactoryProducer>();
  
  protected final SSLSocketFactory wrappedSocketFactory;
  
  /** Set the socket factory producer to use */
  public static void setSocketFactoryProducer(final ISSLSocketFactoryProducer p)
  {
    sslSocketFactoryProducer.set(p);
  }
  
  public LDAPSSLSocketFactory()
    throws ManifoldCFException
  {
    // This must be preinitialized to contain the correct socket factory producer
    this.wrappedSocketFactory = sslSocketFactoryProducer.get().getSecureSocketFactory();
  }
  
  @Override
  public Socket createSocket(final Socket s, final String host, final int port, final boolean autoClose)
    throws IOException
  {
    return wrappedSocketFactory.createSocket(s, host, port, autoClose);
  }

  @Override
  public Socket createSocket(final InetAddress source, final int port, final InetAddress target, final int targetPort)
    throws IOException
  {
    return wrappedSocketFactory.createSocket(source, port, target, targetPort);
  }

  @Override
  public Socket createSocket(final String source, final int port, final InetAddress target, final int targetPort)
    throws IOException
  {
    return wrappedSocketFactory.createSocket(source, port, target, targetPort);
  }

  @Override
  public Socket createSocket(final InetAddress source, final int port)
    throws IOException
  {
    return wrappedSocketFactory.createSocket(source, port);
  }

  @Override
  public Socket createSocket(final String source, final int port)
    throws IOException
  {
    return wrappedSocketFactory.createSocket(source, port);
  }
  
  @Override
  public String[] getDefaultCipherSuites()
  {
    return wrappedSocketFactory.getDefaultCipherSuites();
  }
  
  @Override
  public String[] getSupportedCipherSuites()
  {
    return wrappedSocketFactory.getSupportedCipherSuites();
  }
}
