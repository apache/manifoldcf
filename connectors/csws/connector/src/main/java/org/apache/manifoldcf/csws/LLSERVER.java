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
package org.apache.manifoldcf.livelink;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.common.Base64;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import org.apache.manifoldcf.connectorcommon.interfaces.*;

import java.io.*;
import java.nio.charset.StandardCharsets;

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
  
  private LLSession session = null;
  private File certFolder = null;


  public LLSERVER(boolean useHttp, boolean useSSL, String server, int port, String user, String pwd,
    String httpCgiPath, String httpNtlmDomain, String httpNtlmUser, String httpNtlmPassword,
    IKeystoreManager keystoreManager)
    throws ManifoldCFException
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
    throws ManifoldCFException
  {
    try
    {
      LLValue configuration;

      if (useHttp)
      {
        boolean useNTLM;
        String userNameAndDomain;

        if (httpNtlmDomain != null && httpNtlmUser != null)
        {
          useNTLM = true;
          userNameAndDomain = httpNtlmDomain + "\\" + httpNtlmUser;
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
        if (userNameAndDomain != null)
        {
          configuration.add("HTTPUserName", userNameAndDomain);
          configuration.add("HTTPPassword", httpNtlmPassword);
        }
        if (useNTLM)
          configuration.add("EnableNTLM", LLValue.LL_TRUE);
        else
          configuration.add("EnableNTLM", LLValue.LL_FALSE);

        if (useSSL)
        {
          configuration.add("HTTPS", LLValue.LL_TRUE);
          // Create the place to put the certs
          createCertFolder();
          if (keystore != null)
          {
            // Now, write the certs themselves
            String[] aliases = keystore.getContents();
            for (String alias : aliases)
            {
              java.security.cert.Certificate cert = keystore.getCertificate(alias);
              byte[] certData = cert.getEncoded();
              File fileName = new File(certFolder,ManifoldCF.safeFileName(alias) + ".cer");
              OutputStream fos = new FileOutputStream(fileName);
              try
              {
                Writer osw = new OutputStreamWriter(fos,StandardCharsets.UTF_8);
                try
                {
                  String certBase64 = new Base64().encodeByteArray(certData);
                  osw.write("-----BEGIN CERTIFICATE-----\n");
                  int index = 0;
                  while (true)
                  {
                    if (certBase64.length() - index > 64)
                    {
                      osw.write(certBase64.substring(index,index+64) + "\n");
                      index += 64;
                    }
                    else
                    {
                      osw.write(certBase64.substring(index) + "\n");
                      break;
                    }
                  }
                  osw.write("-----END CERTIFICATE-----\n");
                }
                finally
                {
                  osw.flush();
                }
              }
              finally
              {
                fos.flush();
                fos.close();
              }
            }
          }
          LLValue rootCACertList = new LLValue();
          LLSession.GetCARootCerts(certFolder.toString(),rootCACertList);
          configuration.add("CARootCerts", rootCACertList);
        }
        else
        {
          configuration.add("HTTPS", LLValue.LL_FALSE);
        }
      }
      else
        configuration = null;

      session = new LLSession (this.LLServer, this.LLPort, "", this.LLUser, this.LLPwd, configuration);
    }
    catch (IOException e)
    {
      releaseCertFolder();
      throw new ManifoldCFException("IO Exception writing cert files: "+e.getMessage(),e);
    }
    catch (java.security.cert.CertificateEncodingException e)
    {
      releaseCertFolder();
      throw new ManifoldCFException("Bad certificate: "+e.getMessage(),e);
    }
    catch (ManifoldCFException e)
    {
      releaseCertFolder();
      throw e;
    }
    catch (Error e)
    {
      releaseCertFolder();
      throw e;
    }
    catch (RuntimeException e)
    {
      releaseCertFolder();
      throw e;
    }
  }


  /**
  * Disconnects
  *
  */
  public void disconnect()
  {
    releaseCertFolder();
    session = null;
  }

  /** Create temporary session-bound cert directory.
  */
  protected void createCertFolder()
    throws ManifoldCFException
  {
    certFolder = ManifoldCF.createTempDir("llcrt_",".d");
    ManifoldCF.addFile(certFolder);
  }
  
  /** Release temporary session-bound cert directory.
  */
  protected void releaseCertFolder()
  {
    if (certFolder != null)
    {
      ManifoldCF.deleteFile(certFolder);
      certFolder = null;
    }
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
