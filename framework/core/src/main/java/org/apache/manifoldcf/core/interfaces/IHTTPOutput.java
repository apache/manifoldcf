/* $Id: IHTTPOutput.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This interface abstracts from the output character stream used to construct
* HTML output for a web interface.  More broadly, it provides the services that all
* connectors will need in order to provide UI components.
*/
public interface IHTTPOutput
{
  public static final String _rcsid = "@(#)$Id: IHTTPOutput.java 988245 2010-08-23 18:39:35Z kwright $";

  // Output services

  /** Flush the stream */
  public void flush()
    throws IOException;
  
  /** Write a newline */
  public void newLine()
    throws IOException;
  
  /** Write a boolean */
  public void print(boolean b)
    throws IOException;
  
  /** Write a char */
  public void print(char c)
    throws IOException;
  
  /** Write an array of chars */
  public void print(char[] c)
    throws IOException;
  
  /** Write a double */
  public void print(double d)
    throws IOException;
  
  /** Write a float */
  public void print(float f)
    throws IOException;
  
  /** Write an int */
  public void print(int i)
    throws IOException;
  
  /** Write a long */
  public void print(long l)
    throws IOException;
  
  /** Write an object */
  public void print(Object o)
    throws IOException;
  
  /** Write a string */
  public void print(String s)
    throws IOException;
  
  /** Write a boolean */
  public void println(boolean b)
    throws IOException;
  
  /** Write a char */
  public void println(char c)
    throws IOException;
  
  /** Write an array of chars */
  public void println(char[] c)
    throws IOException;
  
  /** Write a double */
  public void println(double d)
    throws IOException;
  
  /** Write a float */
  public void println(float f)
    throws IOException;
  
  /** Write an int */
  public void println(int i)
    throws IOException;
  
  /** Write a long */
  public void println(long l)
    throws IOException;
  
  /** Write an object */
  public void println(Object o)
    throws IOException;
  
  /** Write a string */
  public void println(String s)
    throws IOException;

  // Password management services.
  // Passwords should not appear in any data sent from the crawler UI to the browser.  The
  // following methods are provided to assist the connector UI components in this task.
  // A connector coder should use these services as follows:
  // - When the password would ordinarily be put into a form element as the current password,
  //   instead use mapPasswordToKey() to create a key and put that in instead.
  // - When the "password" is posted, and the post is processed, use mapKeyToPassword() to
  //   restore the correct password.
  
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
