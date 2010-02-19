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
package org.apache.lcf.core.keystore;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.core.common.*;
import org.apache.lcf.core.system.Logging;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.util.*;
import java.io.*;

/** This interface describes a class that manages keys and certificates in a secure manner.
* It's built on top of the JDK 1.4+ JSSE integration, and provides all the necessary logic
* to work well within the LCF java environment.
*/
public class KeystoreManager implements IKeystoreManager
{
        public static final String _rcsid = "@(#)$Id$";

        // The keystore passcode
        protected String passcode;
        // The keystore itself
        protected KeyStore keystore;

        /** Create the keystore object.
        */
        public KeystoreManager(String passcode)
                throws LCFException
        {
                this.passcode = passcode;
                try
                {
                        keystore = KeyStore.getInstance("JKS");
                        keystore.load(null,passcode.toCharArray());
                }
                catch (KeyStoreException e)
                {
                        throw new LCFException("Keystore exception: "+e.getMessage(),e);
                }
                catch (InterruptedIOException e)
                {
                        throw new LCFException("Interrupted IO: "+e.getMessage(),e,LCFException.INTERRUPTED);
                }
                catch (IOException e)
                {
                        throw new LCFException("IO error creating keystore: "+e.getMessage(),e);
                }
                catch (NoSuchAlgorithmException e)
                {
                        throw new LCFException("Unknown algorithm exception creating keystore: "+e.getMessage(),e);
                }
                catch (CertificateException e)
                {
                        throw new LCFException("Unknown certificate exception creating keystore: "+e.getMessage(),e);
                }
        }

        /** Create the keystore object from an existing base 64 string.
        */
        public KeystoreManager(String passcode, String base64String)
                throws LCFException
        {
                this.passcode = passcode;
                try
                {
                        keystore = KeyStore.getInstance("JKS");
                        byte[] decodedBytes = new Base64().decodeString(base64String);
                        InputStream base64Input = new ByteArrayInputStream(decodedBytes);
                        try
                        {
                                keystore.load(base64Input,passcode.toCharArray());
                        }
                        finally
                        {
                                base64Input.close();
                        }
                }
                catch (KeyStoreException e)
                {
                        throw new LCFException("Keystore exception: "+e.getMessage(),e);
                }
                catch (InterruptedIOException e)
                {
                        throw new LCFException("Interrupted IO: "+e.getMessage(),e,LCFException.INTERRUPTED);
                }
                catch (IOException e)
                {
                        throw new LCFException("IO error creating keystore: "+e.getMessage(),e);
                }
                catch (NoSuchAlgorithmException e)
                {
                        throw new LCFException("Unknown algorithm exception creating keystore: "+e.getMessage(),e);
                }
                catch (CertificateException e)
                {
                        throw new LCFException("Unknown certificate exception creating keystore: "+e.getMessage(),e);
                }
        }

        /** Grab a list of the aliases in the key store.
        *@return the list, as a string array.
        */
        public String[] getContents()
                throws LCFException
        {
                try
                {
                        String[] rval = new String[keystore.size()];
                        Enumeration enum = keystore.aliases();
                        int i = 0;
                        while (enum.hasMoreElements())
                        {
                                String alias = (String)enum.nextElement();
                                rval[i++] = alias;
                        }
                        return rval;
                }
                catch (KeyStoreException e)
                {
                        throw new LCFException("Keystore not initialized: "+e.getMessage(),e);
                }

        }


        /** For an alias, get some descriptive information from the object in the keystore.
        *@param alias is the alias name.
        *@return a description of what's in the alias.
        */
        public String getDescription(String alias)
                throws LCFException
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
                        throw new LCFException("Keystore not initialized: "+e.getMessage(),e);
                }
        }

        /** Import a certificate or key into the list.  The data must be added as binary.
        *@param alias is the name of the certificate.
        *@param certData is the binary data for the certificate.
        */
        public void importCertificate(String alias, InputStream certData)
                throws LCFException
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
                        throw new LCFException("Keystore exception: "+e.getMessage(),e);
                }
                catch (CertificateException e)
                {
                        throw new LCFException("Certificate error: "+e.getMessage(),e);
                }
        }

        /** Read a certificate from the keystore.
        */
        public java.security.cert.Certificate getCertificate(String alias)
                throws LCFException
        {
                try
                {
                        return keystore.getCertificate(alias);
                }
                catch (KeyStoreException e)
                {
                        throw new LCFException("Keystore exception: "+e.getMessage(),e);
                }
        }

        /** Add a certificate to the keystore.
        */
        public void addCertificate(String alias, java.security.cert.Certificate certificate)
                throws LCFException
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
                        throw new LCFException("Keystore exception: "+e.getMessage(),e);
                }
        }

        /** Remove a certificate.
        *@param alias is the name of the certificate to remove.
        */
        public void remove(String alias)
                throws LCFException
        {
                try
                {
                        keystore.deleteEntry(alias);
                }
                catch (KeyStoreException e)
                {
                        throw new LCFException("Error deleting keystore entry",e);
                }
        }

        /** Convert to a base64 string.
        *@return the base64-encoded string.
        */
        public String getString()
                throws LCFException
        {
                try
                {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        try
                        {
                                keystore.store(output,passcode.toCharArray());
                                return new Base64().encodeByteArray(output.toByteArray());
                        }
                        catch (KeyStoreException e)
                        {
                                throw new LCFException("Error accessing keystore: "+e.getMessage(),e);
                        }
                        catch (InterruptedIOException e)
                        {
                                throw new LCFException("Interrupted IO: "+e.getMessage(),e,LCFException.INTERRUPTED);
                        }
                        catch (IOException e)
                        {
                                throw new LCFException("IO error saving keystore: "+e.getMessage(),e);
                        }
                        catch (NoSuchAlgorithmException e)
                        {
                                throw new LCFException("Unknown algorithm exception saving keystore: "+e.getMessage(),e);
                        }
                        catch (CertificateException e)
                        {
                                throw new LCFException("Certificate exception saving keystore: "+e.getMessage(),e);
                        }
                        finally
                        {
                                output.close();
                        }
                }
                catch (InterruptedIOException e)
                {
                        throw new LCFException("Interrupted IO: "+e.getMessage(),e,LCFException.INTERRUPTED);
                }
                catch (IOException e)
                {
                        throw new LCFException("IO exception storing keystore: "+e.getMessage(),e);
                }
        }

        /** Build a secure socket factory based on this keystore.
        */
        public javax.net.ssl.SSLSocketFactory getSecureSocketFactory()
                throws LCFException
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
                throw new LCFException("No such algorithm: "+e.getMessage(),e);
            }
            catch (java.security.KeyStoreException e)
            {
                throw new LCFException("Keystore exception: "+e.getMessage(),e);
            }
            catch (java.security.KeyManagementException e)
            {
                throw new LCFException("Key management exception: "+e.getMessage(),e);
            }
        }



}
