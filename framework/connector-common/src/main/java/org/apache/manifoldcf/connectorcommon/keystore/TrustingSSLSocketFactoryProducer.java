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
package org.apache.manifoldcf.connectorcommon.keystore;

import org.apache.manifoldcf.core.interfaces.*;
import javax.net.ssl.SSLSocketFactory;
import java.security.*;
import java.io.*;
import java.net.Socket;
import java.net.InetAddress;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.TrustManager;
import java.security.cert.X509Certificate;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/** This SSLSocketFactoryProducer does no certificate checking whatsoever.
*/
public class TrustingSSLSocketFactoryProducer implements ISSLSocketFactoryProducer
{
  public TrustingSSLSocketFactoryProducer()
  {
  }
  
  /** Build a secure socket factory based on this producer.
  */
  @Override
  public javax.net.ssl.SSLSocketFactory getSecureSocketFactory()
    throws ManifoldCFException
  {
    try
    {
      final TrustManager tm = new X509TrustManager() {
        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException
        {
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException
        {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers()
        {
          return null;
        }
      };

      final SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[] { tm }, null);
      return sslContext.getSocketFactory();
    }
    catch (NoSuchAlgorithmException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
    catch (KeyManagementException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }

}
