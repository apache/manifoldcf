/* $Id: UTF8Stderr.java 988245 2010-08-23 18:39:35Z kwright $ */

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
public final class UTF8Stderr
{
  public static final String _rcsid = "@(#)$Id: UTF8Stderr.java 988245 2010-08-23 18:39:35Z kwright $";

  private static PrintWriter err;

  // this is called before invoking any methods
  static
  {
     err = new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);
  }

  private UTF8Stderr()
  {
  }

  // Close (not required)
  public static void close()
  {
    err.close();
  }

  // println methods
  public static void println()
  {
    err.println();
  }

  public static void println(Object x)
  {
    err.println(x);
  }

  public static void println(boolean x)
  {
    err.println(x);
  }

  public static void println(char x)
  {
    err.println(x);
  }

  public static void println(double x)
  {
    err.println(x);
  }

  public static void println(float x)
  {
    err.println(x);
  }

  public static void println(int x)
  {
    err.println(x);
  }

  public static void println(long x)
  {
    err.println(x);
  }

  // print methods
  public static void print()
  {
    err.flush();
  }

  public static void print(Object x)
  {
    err.print(x);
    err.flush();
  }

  public static void print(boolean x)
  {
    err.print(x);
    err.flush();
  }

  public static void print(char x)
  {
    err.print(x);
    err.flush();
  }

  public static void print(double x)
  {
    err.print(x);
    err.flush();
  }

  public static void print(float x)
  {
    err.print(x);
    err.flush();
  }

  public static void print(int x)
  {
    err.print(x);
    err.flush();
  }

  public static void print(long x)
  {
    err.print(x);
    err.flush();
  }

  public static void printStackTrace(Throwable e)
  {
    e.printStackTrace(err);
  }

}
