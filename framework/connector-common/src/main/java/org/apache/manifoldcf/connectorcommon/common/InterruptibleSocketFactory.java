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
package org.apache.manifoldcf.connectorcommon.common;

import java.io.*;
import java.net.*;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;
import java.time.Instant;

import org.apache.http.conn.ConnectTimeoutException;

/** SSL Socket factory which wraps another socket factory but allows timeout on socket
* creation.
*/
public class InterruptibleSocketFactory extends javax.net.ssl.SSLSocketFactory
{
  protected final javax.net.ssl.SSLSocketFactory wrappedFactory;
  protected final long connectTimeoutMilliseconds;
    
  public InterruptibleSocketFactory(javax.net.ssl.SSLSocketFactory wrappedFactory, long connectTimeoutMilliseconds)
  {
    this.wrappedFactory = wrappedFactory;
    this.connectTimeoutMilliseconds = connectTimeoutMilliseconds;
  }

  @Override
  public Socket createSocket()
    throws IOException
  {
    // Socket isn't open
    return wrappedFactory.createSocket();
  }
    
  @Override
  public Socket createSocket(String host, int port)
    throws IOException, UnknownHostException
  {
    return fireOffThread(InetAddress.getByName(host),port,null,-1);
  }

  @Override
  public Socket createSocket(InetAddress host, int port)
    throws IOException
  {
    return fireOffThread(host,port,null,-1);
  }
    
  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
    throws IOException, UnknownHostException
  {
    return fireOffThread(InetAddress.getByName(host),port,localHost,localPort);
  }
    
  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
    throws IOException
  {
    return fireOffThread(address,port,localAddress,localPort);
  }
    
  @Override
  public Socket createSocket(Socket s, String host, int port, boolean autoClose)
    throws IOException
  {
    // Socket's already open
    return wrappedFactory.createSocket(s,host,port,autoClose);
  }
    
  @Override
  public String[] getDefaultCipherSuites()
  {
    return wrappedFactory.getDefaultCipherSuites();
  }
    
  @Override
  public String[] getSupportedCipherSuites()
  {
    return wrappedFactory.getSupportedCipherSuites();
  }
    
  protected Socket fireOffThread(InetAddress address, int port, InetAddress localHost, int localPort)
    throws IOException
  {
    try (var scope = StructuredTaskScope.open(
            StructuredTaskScope.Joiner.<Socket>awaitAllSuccessfulOrThrow(),
            config -> config.withTimeout(java.time.Duration.ofMillis(connectTimeoutMilliseconds))))
    {
      StructuredTaskScope.Subtask<Socket> subtask = scope.fork(() -> {
        if (localHost == null)
          return wrappedFactory.createSocket(address, port);
        else
          return wrappedFactory.createSocket(address, port, localHost, localPort);
      });

      try
      {
        scope.join();
        return subtask.get();
      }
      catch (java.util.concurrent.StructuredTaskScope.TimeoutException e)
      {
        throw new ConnectTimeoutException("Secure connection timed out: " + e.getMessage());
      }
      catch (java.util.concurrent.StructuredTaskScope.FailedException e)
      {
        Throwable t = e.getCause();
        if (t instanceof java.net.SocketTimeoutException)
          throw (java.net.SocketTimeoutException)t;
        else if (t instanceof ConnectTimeoutException)
          throw (ConnectTimeoutException)t;
        else if (t instanceof InterruptedIOException)
          throw (InterruptedIOException)t;
        else if (t instanceof IOException)
          throw (IOException)t;
        else if (t instanceof Error)
          throw (Error)t;
        else if (t instanceof RuntimeException)
          throw (RuntimeException)t;
        throw new Error("Received an unexpected exception: "+t.getMessage(),t);
      }
      catch (InterruptedException e)
      {
        throw new InterruptedIOException("Interrupted: "+e.getMessage());
      }
    }
  }

}
