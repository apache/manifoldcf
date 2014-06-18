/* $Id: JspWrapper.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.ui.jsp;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.ui.beans.AdminProfile;
import javax.servlet.jsp.*;
import java.io.*;

/** This class provides an implementation of IHTTPOutput, which provides output
* services to connector UI interfaces.  More broadly, it provides the services that all
* connectors will need in order to provide UI components.
*/
public class JspWrapper implements IHTTPOutput
{
  public static final String _rcsid = "@(#)$Id: JspWrapper.java 988245 2010-08-23 18:39:35Z kwright $";

  protected final JspWriter writer;
  protected final AdminProfile adminProfile;

  /** Constructor.
  */
  public JspWrapper(JspWriter writer, AdminProfile adminProfile)
  {
    this.writer = writer;
    this.adminProfile = adminProfile;
  }

  /** Flush the stream */
  @Override
  public void flush()
    throws IOException
  {
    writer.flush();
  }
  
  /** Write a newline */
  @Override
  public void newLine()
    throws IOException
  {
    writer.newLine();
  }
  
  /** Write a boolean */
  @Override
  public void print(boolean b)
    throws IOException
  {
    writer.print(b);
  }
  
  /** Write a char */
  @Override
  public void print(char c)
    throws IOException
  {
    writer.print(c);
  }
  
  /** Write an array of chars */
  @Override
  public void print(char[] c)
    throws IOException
  {
    writer.print(c);
  }
  
  /** Write a double */
  @Override
  public void print(double d)
    throws IOException
  {
    writer.print(d);
  }
  
  /** Write a float */
  @Override
  public void print(float f)
    throws IOException
  {
    writer.print(f);
  }
  
  /** Write an int */
  @Override
  public void print(int i)
    throws IOException
  {
    writer.print(i);
  }
  
  /** Write a long */
  @Override
  public void print(long l)
    throws IOException
  {
    writer.print(l);
  }
  
  /** Write an object */
  @Override
  public void print(Object o)
    throws IOException
  {
    writer.print(o);
  }
  
  /** Write a string */
  @Override
  public void print(String s)
    throws IOException
  {
    writer.print(s);
  }
  
  /** Write a boolean */
  @Override
  public void println(boolean b)
    throws IOException
  {
    writer.println(b);
  }
  
  /** Write a char */
  @Override
  public void println(char c)
    throws IOException
  {
    writer.println(c);
  }
  
  /** Write an array of chars */
  @Override
  public void println(char[] c)
    throws IOException
  {
    writer.println(c);
  }
  
  /** Write a double */
  @Override
  public void println(double d)
    throws IOException
  {
    writer.println(d);
  }
  
  /** Write a float */
  @Override
  public void println(float f)
    throws IOException
  {
    writer.println(f);
  }
  
  /** Write an int */
  @Override
  public void println(int i)
    throws IOException
  {
    writer.println(i);
  }
  
  /** Write a long */
  @Override
  public void println(long l)
    throws IOException
  {
    writer.println(l);
  }
  
  /** Write an object */
  @Override
  public void println(Object o)
    throws IOException
  {
    writer.println(o);
  }
  
  /** Write a string */
  @Override
  public void println(String s)
    throws IOException
  {
    writer.println(s);
  }

  /** Map a password to a unique key.
  * This method works within a specific given browser session to replace an existing password with
  * a key which can be used to look up the password at a later time.
  *@param password is the password.
  *@return the key.
  */
  @Override
  public String mapPasswordToKey(String password)
  {
    return adminProfile.getPasswordMapper().mapPasswordToKey(password);
  }
  
  /** Convert a key, created by mapPasswordToKey, back to the original password, within
  * the lifetime of the browser session.  If the provided key is not an actual key, instead
  * the key value is assumed to be a new password value.
  *@param key is the key.
  *@return the password.
  */
  @Override
  public String mapKeyToPassword(String key)
  {
    return adminProfile.getPasswordMapper().mapKeyToPassword(key);
  }

}
