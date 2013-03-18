/* $Id: LLSERVER.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.livelink;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;

import com.opentext.api.LLSession;
import com.opentext.api.LLValue;

/**
* @author Riccardo, modified extensively by Karl Wright
*
* This class represents information about a particular
* Livelink Server. It also maintains a particular server session.
* NOTE: The original Volant code insisted at a fundamental level that there be only
* one session per JVM.  Not sure why they did this, and this is a vile restriction
* if true.  I've therefore reworked this class to be able to work in a multi-session
* environment, if possible; the instantiator gets to determine how many there will be.
*/
public class LLSERVER
{
  public static final String _rcsid = "@(#)$Id: LLSERVER.java 988245 2010-08-23 18:39:35Z kwright $";

  private final boolean useHttp;
  private final boolean useSSL;
  private final String LLServer;
  private final int LLPort;
  private final String LLUser;
  private final String LLPwd;
  private final String httpCgiPath;
  private final String httpNtlmDomain;
  private final String httpNtlmUser;
  private final String httpNtlmPassword;
  private final IKeystoreManager keystore;
  
  private LLSession session;


  public LLSERVER(boolean useHttp, boolean useSSL, String server, int port, String user, String pwd,
    String httpCgiPath, String httpNtlmDomain, String httpNtlmUser, String httpNtlmPassword,
    IKeystoreManager keystoreManager)
  {
    this.useHttp = useHttp;
    this.useSSL = useSSL;
    LLServer = server;
    LLPort = port;
    LLUser = user;
    LLPwd = pwd;
    this.httpCgiPath = httpCgiPath;
    this.httpNtlmDomain = httpNtlmDomain;
    this.httpNtlmUser = httpNtlmUser;
    this.httpNtlmPassword = httpNtlmPassword;
    this.keystore = keystoreManager;

    connect();
  }

  private void connect()
  {
    
    LLValue configuration;

    if (useHttp)
    {
      boolean useNTLM;
      String userNameAndDomain;

      if (httpNtlmDomain != null)
      {
        useNTLM = true;
        userNameAndDomain = httpNtlmUser + "@" + httpNtlmDomain;
      }
      else
      {
        useNTLM = false;
        userNameAndDomain = httpNtlmUser;
      }
      configuration = new LLValue();
      configuration.setAssoc();
      configuration.add("Encoding","UTF-8");
      configuration.add("LivelinkCGI", httpCgiPath);
      if (useNTLM)
      {
        configuration.add("HTTPUserName", userNameAndDomain);
        configuration.add("HTTPPassword", httpNtlmPassword);
        configuration.add("EnableNTLM", LLValue.LL_TRUE);
      }
      else
        configuration.add("EnableNTLM", LLValue.LL_FALSE);

      if (useSSL)
      {
        configuration.add("HTTPS", LLValue.LL_TRUE);
        // MHL to create temporary folder with trust certs
      }
    }
    else
      configuration = null;

    session = new LLSession (this.LLServer, this.LLPort, "", this.LLUser, this.LLPwd, configuration);
  }


  /**
  * Disconnects
  *
  */
  public void disconnect()
  {
    // MHL to delete temporary folder with trust certs
    session = null;
  }


  /**
  * Returns the server name where the Livelink
  * Server has been installed on
  *
  * @return the server name
  */
  public String getHost()
  {

    if (session != null)
    {
      return session.getHost();
    }

    return null;
  }


  /**
  * Returns the port Livelink is listening on
  * @return the port number
  */
  public int getPort ()
  {

    if (session != null)
    {
      return session.getPort();
    }

    return -1;
  }


  /**
  * Returns the Livelink user currently connected
  * to the Livelink Server
  * @return the user name
  */
  public String getLLUser()
  {

    return LLUser;
  }



  /**
  * Returns the password of the user currently connected
  * to the Livelink Server
  * @return the user password
  */
  public String getLLPwd()
  {

    return LLPwd;
  }


  /**
  * Returns the Livelink session
  * @return Livelink session
  */
  public LLSession getLLSession()
  {

    return session;
  }

  /**
  * Get the current session errors as a string.
  */
  public String getErrors()
  {
    if (session == null)
      return null;
    StringBuilder rval = new StringBuilder();
    if (session.getStatus() != 0)
      rval.append("LAPI status code: ").append(session.getStatus());
    if (session.getApiError().length() > 0)
    {
      if (rval.length() > 0)
        rval.append("; ");
      rval.append("LAPI error detail: ").append(session.getApiError());
    }
    if (session.getErrMsg().length() > 0)
    {
      if (rval.length() > 0)
        rval.append("; ");
      rval.append("LAPI error message: ").append(session.getErrMsg());
    }
    if (session.getStatusMessage().length() > 0)
    {
      if (rval.length() > 0)
        rval.append("; ");
      rval.append("LAPI status message: ").append(session.getStatusMessage());
    }
    if (rval.length() > 0)
      return rval.toString();
    return null;
  }

}
