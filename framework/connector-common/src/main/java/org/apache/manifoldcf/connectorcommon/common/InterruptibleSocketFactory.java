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
    SocketCreateThread thread = new SocketCreateThread(wrappedFactory,address,port,localHost,localPort);
    thread.start();
    try
    {
      // Wait for thread to complete for only a certain amount of time!
      thread.join(connectTimeoutMilliseconds);
      // If join() times out, then the thread is going to still be alive.
      if (thread.isAlive())
      {
        // Kill the thread - not that this will necessarily work, but we need to try
        thread.interrupt();
        throw new ConnectTimeoutException("Secure connection timed out");
      }
      // The thread terminated.  Throw an error if there is one, otherwise return the result.
      Throwable t = thread.getException();
      if (t != null)
      {
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
      return thread.getResult();
    }
    catch (InterruptedException e)
    {
      throw new InterruptedIOException("Interrupted: "+e.getMessage());
    }

  }

  /** Create a secure socket in a thread, so that we can "give up" after a while if the socket fails to connect.
  */
  protected static class SocketCreateThread extends Thread
  {
    // Socket factory
    protected javax.net.ssl.SSLSocketFactory socketFactory;
    protected InetAddress host;
    protected int port;
    protected InetAddress clientHost;
    protected int clientPort;

    // The return socket
    protected Socket rval = null;
    // The return error
    protected Throwable throwable = null;

    /** Create the thread */
    public SocketCreateThread(javax.net.ssl.SSLSocketFactory socketFactory,
      InetAddress host,
      int port,
      InetAddress clientHost,
      int clientPort)
    {
      this.socketFactory = socketFactory;
      this.host = host;
      this.port = port;
      this.clientHost = clientHost;
      this.clientPort = clientPort;
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        if (clientHost == null)
          rval = socketFactory.createSocket(host,port);
        else
          rval = socketFactory.createSocket(host,port,clientHost,clientPort);
      }
      catch (Throwable e)
      {
        throwable = e;
      }
    }

    public Throwable getException()
    {
      return throwable;
    }

    public Socket getResult()
    {
      return rval;
    }
  }

}
