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
package org.apache.manifoldcf.core.interfaces;

import java.io.*;

/** This interface abstracts from password mapping activity, available for
* all connector-provided UI components.
* Passwords should not appear in any data sent from the crawler UI to the browser.  The
* following methods are provided to assist the connector UI components in this task.
* A connector coder should use these services as follows:
* - When the password would ordinarily be put into a form element as the current password,
*    instead use mapPasswordToKey() to create a key and put that in instead.
* - When the "password" is posted, and the post is processed, use mapKeyToPassword() to
*    restore the correct password.
*/
public interface IPasswordMapperActivity
{
  public static final String _rcsid = "@(#)$Id$";

  /** Map a password to a unique key.
  * This method works within a specific given browser session to replace an existing password with
  * a key which can be used to look up the password at a later time.
  *@param password is the password.
  *@return the key.
  */
  public String mapPasswordToKey(String password);
  
  /** Convert a key, created by mapPasswordToKey, back to the original password, within
  * the lifetime of the browser session.  If the provided key is not an actual key, instead
  * the key value is assumed to be a new password value.
  *@param key is the key.
  *@return the password.
  */
  public String mapKeyToPassword(String key);
  
}
