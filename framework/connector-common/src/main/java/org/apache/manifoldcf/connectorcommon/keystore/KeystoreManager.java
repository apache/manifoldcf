/* $Id: KeystoreManager.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.connectorcommon.interfaces.*;
import org.apache.manifoldcf.core.common.*;
import org.apache.manifoldcf.core.system.Logging;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.util.*;
import java.io.*;

/** This interface describes a class that manages keys and certificates in a secure manner.
* It's built on top of the JDK 1.4+ JSSE integration, and provides all the necessary logic
* to work well within the ManifoldCF java environment.
*/
public class KeystoreManager implements IKeystoreManager
{
  public static final String _rcsid = "@(#)$Id: KeystoreManager.java 988245 2010-08-23 18:39:35Z kwright $";

  // The keystore passcode
  protected final String passcode;
  // The keystore itself
  protected final KeyStore keystore;

  /** Create the keystore object.
  */
  public KeystoreManager(String passcode)
    throws ManifoldCFException
  {
    this.passcode = passcode;
    try
    {
      keystore = KeyStore.getInstance("JKS");
      keystore.load(null,passcode.toCharArray());
    }
    catch (KeyStoreException e)
    {
      throw new ManifoldCFException("Keystore exception: "+e.getMessage(),e);
    }
    catch (InterruptedIOException e)
    {
      throw new ManifoldCFException("Interrupted IO: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO error creating keystore: "+e.getMessage(),e);
    }
    catch (NoSuchAlgorithmException e)
    {
      throw new ManifoldCFException("Unknown algorithm exception creating keystore: "+e.getMessage(),e);
    }
    catch (CertificateException e)
    {
      throw new ManifoldCFException("Unknown certificate exception creating keystore: "+e.getMessage(),e);
    }
  }

  /** Create the keystore object from an existing base 64 string.
  */
  public KeystoreManager(String passcode, String base64String)
    throws ManifoldCFException
  {
    this.passcode = passcode;
    try
    {
      keystore = KeyStore.getInstance("JKS");
      byte[] decodedBytes = new org.apache.manifoldcf.core.common.Base64().decodeString(base64String);

      try(InputStream base64Input = new ByteArrayInputStream(decodedBytes))
      {
        keystore.load(base64Input,passcode.toCharArray());
      }
    }
    catch (KeyStoreException e)
    {
      throw new ManifoldCFException("Keystore exception: "+e.getMessage(),e);
    }
    catch (InterruptedIOException e)
    {
      throw new ManifoldCFException("Interrupted IO: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO error creating keystore: "+e.getMessage(),e);
    }
    catch (NoSuchAlgorithmException e)
    {
      throw new ManifoldCFException("Unknown algorithm exception creating keystore: "+e.getMessage(),e);
    }
    catch (CertificateException e)
    {
      throw new ManifoldCFException("Unknown certificate exception creating keystore: "+e.getMessage(),e);
    }
  }

  /** Get a unique hashstring for this keystore.  The hashcode depends only on the certificates
  * in the store.
  *@return the hash string for this keystore.
  */
  @Override
  public String getHashString()
    throws ManifoldCFException
  {
    StringBuilder sb = new StringBuilder();
    // Get the certs in the store
    String[] aliases = getContents();
    for (String alias : aliases)
    {
      String description = getDescription(alias);
      sb.append(":").append(alias).append(":").append(description);
    }
    return sb.toString();
  }

  /** Grab a list of the aliases in the key store.
  *@return the list, as a string array.
  */
  @Override
  public String[] getContents()
    throws ManifoldCFException
  {
    try
    {
      String[] rval = new String[keystore.size()];
      Enumeration enumeration = keystore.aliases();
      int i = 0;
      while (enumeration.hasMoreElements())
      {
        String alias = (String)enumeration.nextElement();
        rval[i++] = alias;
      }
      return rval;
    }
    catch (KeyStoreException e)
    {
      throw new ManifoldCFException("Keystore not initialized: "+e.getMessage(),e);
    }

  }


  /** For an alias, get some descriptive information from the object in the keystore.
  *@param alias is the alias name.
  *@return a description of what's in the alias.
  */
  @Override
  public String getDescription(String alias)
    throws ManifoldCFException
  {
    try
    {
      Certificate c = keystore.getCertificate(alias);
      if (c == null)
        return null;
      return c.toString();
    }
    catch (KeyStoreException e)
    {
      throw new ManifoldCFException("Keystore not initialized: "+e.getMessage(),e);
    }
  }

  /** Import a certificate or key into the list.  The data must be added as binary.
  *@param alias is the name of the certificate.
  *@param certData is the binary data for the certificate.
  */
  @Override
  public void importCertificate(String alias, InputStream certData)
    throws ManifoldCFException
  {
    try
    {
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      Certificate c = cf.generateCertificate(certData);
      keystore.setCertificateEntry(alias,c);
      if (Logging.keystore.isDebugEnabled())
      {
        if (keystore.isCertificateEntry(alias))
          Logging.keystore.debug("The certificate just imported is a Trust Certificate");
        else
          Logging.keystore.debug("The certificate just imported is NOT a Trust Certificate");
      }
    }
    catch (KeyStoreException e)
    {
      throw new ManifoldCFException("Keystore exception: "+e.getMessage(),e);
    }
    catch (CertificateException e)
    {
      throw new ManifoldCFException("Certificate error: "+e.getMessage(),e);
    }
  }

  /** Read a certificate from the keystore.
  */
  @Override
  public java.security.cert.Certificate getCertificate(String alias)
    throws ManifoldCFException
  {
    try
    {
      return keystore.getCertificate(alias);
    }
    catch (KeyStoreException e)
    {
      throw new ManifoldCFException("Keystore exception: "+e.getMessage(),e);
    }
  }

  /** Add a certificate to the keystore.
  */
  @Override
  public void addCertificate(String alias, java.security.cert.Certificate certificate)
    throws ManifoldCFException
  {
    try
    {
      keystore.setCertificateEntry(alias,certificate);
      if (Logging.keystore.isDebugEnabled())
      {
        if (keystore.isCertificateEntry(alias))
          Logging.keystore.debug("The certificate just added is a Trust Certificate");
        else
          Logging.keystore.debug("The certificate just added is NOT a Trust Certificate");
      }
    }
    catch (KeyStoreException e)
    {
      throw new ManifoldCFException("Keystore exception: "+e.getMessage(),e);
    }
  }

  /** Remove a certificate.
  *@param alias is the name of the certificate to remove.
  */
  @Override
  public void remove(String alias)
    throws ManifoldCFException
  {
    try
    {
      keystore.deleteEntry(alias);
    }
    catch (KeyStoreException e)
    {
      throw new ManifoldCFException("Error deleting keystore entry",e);
    }
  }

  /** Convert to a base64 string.
  *@return the base64-encoded string.  NOTE WELL: as of JDK 1.6, you will not get the same exact string twice from this method --
  *  so it cannot be used for a hash!!
  */
  @Override
  public String getString()
    throws ManifoldCFException
  {
    try
    {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      try
      {
        keystore.store(output,passcode.toCharArray());
        return new org.apache.manifoldcf.core.common.Base64().encodeByteArray(output.toByteArray());
      }
      catch (KeyStoreException e)
      {
        throw new ManifoldCFException("Error accessing keystore: "+e.getMessage(),e);
      }
      catch (InterruptedIOException e)
      {
        throw new ManifoldCFException("Interrupted IO: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (IOException e)
      {
        throw new ManifoldCFException("IO error saving keystore: "+e.getMessage(),e);
      }
      catch (NoSuchAlgorithmException e)
      {
        throw new ManifoldCFException("Unknown algorithm exception saving keystore: "+e.getMessage(),e);
      }
      catch (CertificateException e)
      {
        throw new ManifoldCFException("Certificate exception saving keystore: "+e.getMessage(),e);
      }
      finally
      {
        output.close();
      }
    }
    catch (InterruptedIOException e)
    {
      throw new ManifoldCFException("Interrupted IO: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO exception storing keystore: "+e.getMessage(),e);
    }
  }

  /** Build a secure socket factory based on this keystore.
  */
  @Override
  public javax.net.ssl.SSLSocketFactory getSecureSocketFactory()
    throws ManifoldCFException
  {
    try
    {
      // Construct a key manager and a trust manager
      javax.net.ssl.KeyManagerFactory keyManagerFactory = null;
      // javax.net.ssl.KeyManagerFactory keyManagerFactory = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
      // keyManagerFactory.init(keystore,passcode);

      javax.net.ssl.TrustManagerFactory trustManagerFactory = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
      Logging.keystore.debug("Contents of current trust keystore is:");
      if (Logging.keystore.isDebugEnabled())
      {
        String[] contents = getContents();
        int i = 0;
        while (i < contents.length)
        {
          Logging.keystore.debug("Description "+Integer.toString(i)+": "+getDescription(contents[i]));
          i++;
        }
      }
      Logging.keystore.debug("Reading trust keystore...");
      trustManagerFactory.init(keystore);
      if (Logging.keystore.isDebugEnabled())
      {
        Logging.keystore.debug("...done");
        javax.net.ssl.TrustManager array[] = trustManagerFactory.getTrustManagers();
        Logging.keystore.debug("Found "+Integer.toString(array.length)+" trust managers");
        int i = 0;
        while (i < array.length)
        {
          javax.net.ssl.TrustManager tm = array[i];
          if (tm instanceof javax.net.ssl.X509TrustManager)
          {
            Logging.keystore.debug("Trust manager "+Integer.toString(i)+" is an x509 trust manager; it's class is "+tm.getClass().getName());
            javax.net.ssl.X509TrustManager tm2 = (javax.net.ssl.X509TrustManager)tm;
            java.security.cert.X509Certificate calist[] = tm2.getAcceptedIssuers();
            Logging.keystore.debug("There are "+Integer.toString(calist.length)+" accepted issuers");
            int j = 0;
            while (j < calist.length)
            {
              String value = calist[j].getSubjectDN().toString();
              Logging.keystore.debug("Authority "+Integer.toString(j)+" is "+value);
              j++;
            }
          }
          i++;
        }
        Logging.keystore.debug("No more trust contents");
      }

      java.security.SecureRandom secureRandom = java.security.SecureRandom.getInstance("SHA1PRNG");

      // Create an SSL context
      javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
      sslContext.init(((keyManagerFactory==null)?null:keyManagerFactory.getKeyManagers()),((trustManagerFactory==null)?null:trustManagerFactory.getTrustManagers()),
        secureRandom);

      return sslContext.getSocketFactory();
    }
    catch (java.security.NoSuchAlgorithmException e)
    {
      throw new ManifoldCFException("No such algorithm: "+e.getMessage(),e);
    }
    catch (java.security.KeyStoreException e)
    {
      throw new ManifoldCFException("Keystore exception: "+e.getMessage(),e);
    }
    catch (java.security.KeyManagementException e)
    {
      throw new ManifoldCFException("Key management exception: "+e.getMessage(),e);
    }
  }



}
