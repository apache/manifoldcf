/* $Id: ThreadContext.java 921329 2010-03-10 12:44:20Z kwright $ */

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
package org.apache.acf.ui.beans;

import org.apache.acf.core.interfaces.*;

/** Class to provide a threadcontext bean for every JSP page
*/
public class ThreadContext
{
  public static final String _rcsid = "@(#)$Id: ThreadContext.java 921329 2010-03-10 12:44:20Z kwright $";

  /** thread context interface
  */
  private IThreadContext itc;

  /** Constructor
  */
  public ThreadContext()
  {

    itc = ThreadContextFactory.make();
  }

  /** Get the thread context.
  *@return the context.
  */
  public IThreadContext getThreadContext()
  {
    return itc;
  }
}


