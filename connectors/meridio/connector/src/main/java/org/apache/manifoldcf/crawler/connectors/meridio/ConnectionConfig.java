/* $Id: ConnectionConfig.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.meridio;

import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolFactory;
import org.apache.commons.httpclient.HttpConnectionManager;
import java.util.Map;

/** This class contains configuration information passed to the transport layer as a single, unified object.
* It replaces all other http client configuration that happens in the CommonsHttpClient module.
*/
public class ConnectionConfig
{
  public static final String _rcsid = "@(#)$Id: ConnectionConfig.java 988245 2010-08-23 18:39:35Z kwright $";

  protected ProtocolFactory protocolFactory;
  protected HttpConnectionManager connectionManager;
  protected String proxyHost;
  protected Integer proxyPort;
  protected String domain;
  protected String userName;
  protected String password;

  /** Constructor. */
  public ConnectionConfig(ProtocolFactory protocolFactory, HttpConnectionManager connectionManager, String proxyHost, Integer proxyPort, String domain,
    String userName, String password)
  {
    this.protocolFactory = protocolFactory;
    this.connectionManager = connectionManager;
    this.proxyHost = proxyHost;
    this.proxyPort = proxyPort;
    this.domain = domain;
    this.userName = userName;
    this.password = password;
  }

  /** Get the protocol factory to use for this connection */
  public ProtocolFactory getProtocolFactory()
  {
    return protocolFactory;
  }

  /** Get the protocol given a string protocol name */
  public Protocol getProtocol(String protocolName)
  {
    return (Protocol)protocolFactory.getProtocol(protocolName);
  }

  /** Get the connection manager */
  public HttpConnectionManager getConnectionManager()
  {
    return connectionManager;
  }

  /** Get the proxy host */
  public String getProxyHost()
  {
    return proxyHost;
  }

  /** Get the proxy port */
  public Integer getProxyPort()
  {
    return proxyPort;
  }

  /** Get the domain */
  public String getDomain()
  {
    return domain;
  }

  /** Get the user name */
  public String getUserName()
  {
    return userName;
  }

  /** Get the password */
  public String getPassword()
  {
    return password;
  }

}

