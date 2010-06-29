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
package org.apache.lcf.ui.jsp;

import org.apache.lcf.core.interfaces.*;
import javax.servlet.jsp.*;
import java.io.*;

/** This class provides an implementation of IHTTPOutput, which provides output
* services to connector UI interfaces.
*/
public class JspWrapper implements IHTTPOutput
{
  public static final String _rcsid = "@(#)$Id$";

  protected JspWriter writer;

  /** Constructor.
  */
  public JspWrapper(JspWriter writer)
  {
    this.writer = writer;
  }

  /** Flush the stream */
  public void flush()
    throws IOException
  {
    writer.flush();
  }
  
  /** Write a newline */
  public void newLine()
    throws IOException
  {
    writer.newLine();
  }
  
  /** Write a boolean */
  public void print(boolean b)
    throws IOException
  {
    writer.print(b);
  }
  
  /** Write a char */
  public void print(char c)
    throws IOException
  {
    writer.print(c);
  }
  
  /** Write an array of chars */
  public void print(char[] c)
    throws IOException
  {
    writer.print(c);
  }
  
  /** Write a double */
  public void print(double d)
    throws IOException
  {
    writer.print(d);
  }
  
  /** Write a float */
  public void print(float f)
    throws IOException
  {
    writer.print(f);
  }
  
  /** Write an int */
  public void print(int i)
    throws IOException
  {
    writer.print(i);
  }
  
  /** Write a long */
  public void print(long l)
    throws IOException
  {
    writer.print(l);
  }
  
  /** Write an object */
  public void print(Object o)
    throws IOException
  {
    writer.print(o);
  }
  
  /** Write a string */
  public void print(String s)
    throws IOException
  {
    writer.print(s);
  }
  
  /** Write a boolean */
  public void println(boolean b)
    throws IOException
  {
    writer.println(b);
  }
  
  /** Write a char */
  public void println(char c)
    throws IOException
  {
    writer.println(c);
  }
  
  /** Write an array of chars */
  public void println(char[] c)
    throws IOException
  {
    writer.println(c);
  }
  
  /** Write a double */
  public void println(double d)
    throws IOException
  {
    writer.println(d);
  }
  
  /** Write a float */
  public void println(float f)
    throws IOException
  {
    writer.println(f);
  }
  
  /** Write an int */
  public void println(int i)
    throws IOException
  {
    writer.println(i);
  }
  
  /** Write a long */
  public void println(long l)
    throws IOException
  {
    writer.println(l);
  }
  
  /** Write an object */
  public void println(Object o)
    throws IOException
  {
    writer.println(o);
  }
  
  /** Write a string */
  public void println(String s)
    throws IOException
  {
    writer.println(s);
  }

}
