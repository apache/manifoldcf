/* $Id: ThreadContext.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core.threadcontext;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;

/** Thread context implementation
*/
public class ThreadContext implements IThreadContext
{
  public static final String _rcsid = "@(#)$Id: ThreadContext.java 988245 2010-08-23 18:39:35Z kwright $";

  protected Hashtable hashtable = new Hashtable();

  public ThreadContext()
  {
  }

  /** Set a named object into the context.
  * @param key is the name of the object (usually a string)
  * @param object is the object to save, or null if the object is to be
  * destroyed instead.
  */
  public void save(Object key, Object object)
  {
    if (object == null)
      hashtable.remove(key);
    else
      hashtable.put(key,object);
  }

  /** Retrieve a named object from the context.
  * Use an equivalent key to retrieve what was previously saved.
  * If no such object exists, null will be returned.
  * @param key is the object's key (usually a string)
  * @return the object, or null.
  */
  public Object get(Object key)
  {
    return hashtable.get(key);
  }

}
