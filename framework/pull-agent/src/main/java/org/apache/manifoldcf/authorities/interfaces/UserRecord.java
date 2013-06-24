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

package org.apache.manifoldcf.authorities.interfaces;

import java.util.*;

/** An instance of this class represents everything known about a specific
* user's identification.  Since we don't know in advance how many and what
* kinds of systems are involved, it has been made as flexible as possible, within
* certain constraints.
*
* The overall idea is that there are multiple "domains" (not to be confused with
* Active Directory domains), each with a corresponding bit of user information.
* The user information itself can include further domain specification. For
* example, "activedirectory"->"qa.ad.76.foo.com"->"johnqpublic".
*
* An authority usually looks for a username with a specific domain.  If the domain
* for the user doesn't exist, the authority will consider itself to be offline, and
* lock out any documents that are under jurisdiction of that authority.  Essentially
* it is the equivalent of "user not found".
*
* Authorities can be written to understand multiple domains.  In this case, there's
* usually a priority; one domain takes precedence of the other.
*
* Finally, the way domains get created in the UserRecord is through a Mapper.
* Mappers are plugins which have their own UI and can be configured.  One
* mapper supplied out-of-the-box with ManifoldCF right now is a simple regular-
* expression mapper, which converts the active-directory id to a user name in
* any other specified domain.
*
* NOTE: since the same instance is potentially operated on by multiple threads,
* it must be thread safe.
*/
public class UserRecord
{
  
  // Public well-known domains
  
  /** Active directory domain */
  public final static String DOMAIN_ACTIVEDIRECTORY = "activedirectory";
  
  protected Map<String,Object> userInfo = new HashMap<String,Object>();
  
  /** Constructor */
  public UserRecord()
  {
  }
  
  /** Set a domain value to be a user record */
  public synchronized void setDomainValue(String domain, UserRecord record)
  {
    userInfo.put(domain, record);
  }
  
  /** Set a domain value to be a string */
  public synchronized void setDomainValue(String domain, String name)
  {
    userInfo.put(domain, name);
  }
  
  /** Delete a domain value */
  public synchronized void deleteDomainValue(String domain)
  {
    userInfo.remove(domain);
  }
  
  /** Get a domain value, expecting a String */
  public synchronized String getDomainValueAsString(String domain)
  {
    Object o = userInfo.get(domain);
    if (o == null || !(o instanceof String))
      return null;
    return (String)o;
  }
  
  /** Get a domain value, expecting a UserRecord */
  public synchronized UserRecord getDomainValueAsUserRecord(String domain)
  {
    Object o = userInfo.get(domain);
    if (o == null || !(o instanceof UserRecord))
      return null;
    return (UserRecord)o;
  }
  
  /** Get an iterator over the list of domains */
  public synchronized Iterator<String> iteratorDomains()
  {
    return userInfo.keySet().iterator();
  }
  
  /** Get the number of domains */
  public synchronized int getDomainCount()
  {
    return userInfo.size();
  }
}

