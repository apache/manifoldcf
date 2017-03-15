/* $Id: IKeystoreManager.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import org.apache.manifoldcf.core.interfaces.*;
import java.io.*;

/** This interface describes a class that manages keys and certificates in a secure manner.
* It's built on top of the JDK 1.4+ JSSE integration, and provides all the necessary logic
* to work well within the ManifoldCF java environment.
*/
public interface IKeystoreManager extends ISSLSocketFactoryProducer
{
  public static final String _rcsid = "@(#)$Id: IKeystoreManager.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Get a unique hashstring for this keystore.  The hashcode depends only on the certificates
  * in the store.
  *@return the hash string for this keystore.
  */
  public String getHashString()
    throws ManifoldCFException;

  /** Grab a list of the aliases in the key store.
  *@return the list, as a string array.
  */
  public String[] getContents()
    throws ManifoldCFException;

  /** For an alias, get some descriptive information from the object in the keystore.
  *@param alias is the alias name.
  *@return a description of what's in the alias.
  */
  public String getDescription(String alias)
    throws ManifoldCFException;

  /** Import a certificate or key into the list.  The data must be added as binary.
  *@param alias is the name of the certificate.
  *@param certData is the binary data for the certificate.
  */
  public void importCertificate(String alias, InputStream certData)
    throws ManifoldCFException;

  /** Remove a certificate.
  *@param alias is the name of the certificate to remove.
  */
  public void remove(String alias)
    throws ManifoldCFException;

  /** Convert to a base64 string.
  *@return the base64-encoded string.  This differs every time it is called, and thus
  * CANNOT be used for hashing.
  */
  public String getString()
    throws ManifoldCFException;

  /** Read a certificate from the keystore.
  */
  public java.security.cert.Certificate getCertificate(String alias)
    throws ManifoldCFException;

  /** Add a certificate to the keystore.
  */
  public void addCertificate(String alias, java.security.cert.Certificate certificate)
    throws ManifoldCFException;

}
