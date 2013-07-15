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
package org.apache.manifoldcf.ui.passwords;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.*;
import java.util.*;

/** This object manages a session-based map of password keys.
*/
public class PasswordMapper
{
  public static final String _rcsid = "@(#)$Id$";
  
  private final String randomPrefix;
  private final Map<String,Integer> passwordToKey = new HashMap<String,Integer>();
  private final List<String> passwordList = new ArrayList<String>();
  
  /** Constructor */
  public PasswordMapper()
  {
    randomPrefix = generateRandomPrefix();
  }
  
  /** Map a password to a key.
  *@param password is the password.
  *@return the key.
  */
  public synchronized String mapPasswordToKey(String password)
  {
    // Special case for null or empty password
    if (password == null || password.length() == 0)
      return password;
    Integer index = passwordToKey.get(password);
    if (index == null)
    {
      // Need a new key.
      index = new Integer(passwordList.size());
      passwordList.add(password);
      passwordToKey.put(password,index);
    }
    return randomPrefix + index;
  }
  
  /** Map a key back to a password.
  *@param key is the key (or a password, if changed)
  *@return the password.
  */
  public synchronized String mapKeyToPassword(String key)
  {
    if (key != null && key.startsWith(randomPrefix))
    {
      String intPart = key.substring(randomPrefix.length());
      try
      {
        int index = Integer.parseInt(intPart);
        if (index < passwordList.size())
          return passwordList.get(index);
      }
      catch (NumberFormatException e)
      {
      }
    }
    return key;
  }
  
  // Protected methods
  
  protected static char[] pickChars = new char[]{'\u0d5d','\u20c4','\u0392','\u1a2b'};
  
  /** Generate a random prefix that will not likely collide with any password */
  protected static String generateRandomPrefix()
  {
    Random r = new Random(System.currentTimeMillis());
    StringBuilder sb = new StringBuilder("_");
    for (int i = 0; i < 8; i++)
    {
      int index = r.nextInt(pickChars.length);
      sb.append(pickChars[index]);
    }
    sb.append("_");
    return sb.toString();
  }
  
}
