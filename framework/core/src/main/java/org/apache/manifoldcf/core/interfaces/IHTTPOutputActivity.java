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

/** This interface abstracts from the output character stream used to construct
* HTML output for a web interface.
*/
public interface IHTTPOutputActivity
{
  public static final String _rcsid = "@(#)$Id$";

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

}
