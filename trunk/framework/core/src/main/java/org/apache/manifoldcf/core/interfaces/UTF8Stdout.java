/* $Id: UTF8Stdout.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** This factory returns a database interface appropriate for
* a specified client connection.  The client or company name is
* passed into the factory, as well as a thread context.
*/
public final class UTF8Stdout
{
  public static final String _rcsid = "@(#)$Id: UTF8Stdout.java 988245 2010-08-23 18:39:35Z kwright $";

  private static PrintWriter out;

  // this is called before invoking any methods
  static
  {
    out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
  }

  private UTF8Stdout()
  {
  }

  // Close (not required)
  public static void close()
  {
    out.close();
  }

  // println methods
  public static void println()
  {
    out.println();
  }

  public static void println(Object x)
  {
    out.println(x);
  }

  public static void println(boolean x)
  {
    out.println(x);
  }

  public static void println(char x)
  {
    out.println(x);
  }

  public static void println(double x)
  {
    out.println(x);
  }

  public static void println(float x)
  {
    out.println(x);
  }

  public static void println(int x)
  {
    out.println(x);
  }

  public static void println(long x)
  {
    out.println(x);
  }

  // print methods
  public static void print()
  {
    out.flush();
  }

  public static void print(Object x)
  {
    out.print(x);
    out.flush();
  }

  public static void print(boolean x)
  {
    out.print(x);
    out.flush();
  }

  public static void print(char x)
  {
    out.print(x);
    out.flush();
  }

  public static void print(double x)
  {
    out.print(x);
    out.flush();
  }

  public static void print(float x)
  {
    out.print(x);
    out.flush();
  }

  public static void print(int x)
  {
    out.print(x);
    out.flush();
  }

  public static void print(long x)
  {
    out.print(x);
    out.flush();
  }

  public static void printStackTrace(Throwable e)
  {
    e.printStackTrace(out);
  }

}
