/* $Id: KeystoreManagerFactory.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.connectorcommon.interfaces;

import org.apache.manifoldcf.connectorcommon.keystore.KeystoreManager;

import org.apache.manifoldcf.core.interfaces.*;

import java.security.*;
import java.security.cert.*;

/** This class is the factory class for keystore managers.
*/
public class KeystoreManagerFactory
{
  public static final String _rcsid = "@(#)$Id: KeystoreManagerFactory.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Mint a keystore manager.
  */
  public static IKeystoreManager make(String passcode)
    throws ManifoldCFException
  {
    return new KeystoreManager(passcode);
  }

  /** Mint a keystore manager from a base-64 encoded string.
  */
  public static IKeystoreManager make(String passcode, String base64String)
    throws ManifoldCFException
  {
    return new KeystoreManager(passcode,base64String);
  }

  protected static javax.net.ssl.X509TrustManager[] openTrustManagerArray = new OpenTrustManager[]{new OpenTrustManager()};

  /** Build a secure socket factory that pays no attention to certificates in trust store, and just trusts everything.
  */
  public static javax.net.ssl.SSLSocketFactory getTrustingSecureSocketFactory()
    throws ManifoldCFException
  {
    try
    {
      java.security.SecureRandom secureRandom = java.security.SecureRandom.getInstance("SHA1PRNG");

      // Create an SSL context
      javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
      sslContext.init(null,openTrustManagerArray,secureRandom);

      return sslContext.getSocketFactory();
    }
    catch (java.security.NoSuchAlgorithmException e)
    {
      throw new ManifoldCFException("No such algorithm: "+e.getMessage(),e);
    }
    catch (java.security.KeyManagementException e)
    {
      throw new ManifoldCFException("Key management exception: "+e.getMessage(),e);
    }
  }

  protected static class OpenTrustManager implements javax.net.ssl.X509TrustManager
  {
    public void checkClientTrusted(X509Certificate[] chain,
      String authType)
      throws CertificateException
    {
    }

    public void checkServerTrusted(X509Certificate[] chain,
      String authType)
      throws CertificateException
    {
    }

    public X509Certificate[] getAcceptedIssuers()
    {
      return new X509Certificate[0];
    }
  }

}
